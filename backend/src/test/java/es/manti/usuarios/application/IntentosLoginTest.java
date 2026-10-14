package es.manti.usuarios.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #10/#5 — lógica pura del registro de intentos de login: backoff
 * exponencial por cuenta y ventana deslizante por par sha256(IP+email), con
 * reloj controlado (sin esperar minutos reales).
 */
class IntentosLoginTest {

    private static final long MINUTO = 60_000L;

    /** Config de prueba: umbral 3, backoff 1m x2 (tope 15m), par 2/min. */
    private static LoginProteccionProperties propiedades() {
        return new LoginProteccionProperties(true, 3,
                Duration.ofMinutes(1), 2, Duration.ofMinutes(15),
                Duration.ofMinutes(30), 2);
    }

    private long ahoraMs = 1_000_000L;

    private IntentosLogin intentos() {
        return new IntentosLogin(propiedades(), () -> ahoraMs);
    }

    @Test
    void fallosPorDebajoDelUmbral_noBloquean() {
        IntentosLogin intentos = intentos();
        intentos.registrarFallo("ana@example.com");
        intentos.registrarFallo("ana@example.com");

        assertThat(intentos.esperaBloqueoMs("ana@example.com")).isZero();
    }

    @Test
    void trasElUmbral_cadaFalloAlargaElBloqueoExponencialmente() {
        IntentosLogin intentos = intentos();
        intentos.registrarFallo("ana@example.com");
        intentos.registrarFallo("ana@example.com");
        intentos.registrarFallo("ana@example.com");   // fallo nº3 = umbral → 1 min

        assertThat(intentos.esperaBloqueoMs("ana@example.com")).isEqualTo(MINUTO);

        intentos.registrarFallo("ana@example.com");   // nº4 → 2 min
        assertThat(intentos.esperaBloqueoMs("ana@example.com")).isEqualTo(2 * MINUTO);

        intentos.registrarFallo("ana@example.com");   // nº5 → 4 min
        assertThat(intentos.esperaBloqueoMs("ana@example.com")).isEqualTo(4 * MINUTO);
    }

    @Test
    void bloqueo_llegaAlTope_yNoSigueCreciendo() {
        IntentosLogin intentos = intentos();
        for (int i = 0; i < 12; i++) {
            intentos.registrarFallo("ana@example.com");
        }
        assertThat(intentos.esperaBloqueoMs("ana@example.com"))
                .as("tope de 15 minutos")
                .isEqualTo(15 * MINUTO);
    }

    @Test
    void bloqueo_cuentaAfectadaSoloASuEmail() {
        IntentosLogin intentos = intentos();
        for (int i = 0; i < 3; i++) {
            intentos.registrarFallo("ana@example.com");
        }
        assertThat(intentos.esperaBloqueoMs("ana@example.com")).isEqualTo(MINUTO);
        assertThat(intentos.esperaBloqueoMs("bob@example.com")).isZero();
    }

    @Test
    void bloqueo_expiraTrasSuDuracion() {
        IntentosLogin intentos = intentos();
        for (int i = 0; i < 3; i++) {
            intentos.registrarFallo("ana@example.com");
        }
        ahoraMs += MINUTO + 1;
        assertThat(intentos.esperaBloqueoMs("ana@example.com"))
                .as("el bloqueo de 1 min ya pasó")
                .isZero();
    }

    @Test
    void acierto_reseteaElContador() {
        IntentosLogin intentos = intentos();
        intentos.registrarFallo("ana@example.com");
        intentos.registrarFallo("ana@example.com");
        intentos.registrarAcierto("ana@example.com");
        intentos.registrarFallo("ana@example.com");

        // Tras el reset, el fallo posterior es el nº1 del nuevo conteo.
        assertThat(intentos.esperaBloqueoMs("ana@example.com")).isZero();
    }

    @Test
    void email_seNormalizaEnElContador() {
        IntentosLogin intentos = intentos();
        intentos.registrarFallo("  ANA@Example.COM  ");
        intentos.registrarFallo("ana@example.com");
        intentos.registrarFallo("ana@example.com");

        assertThat(intentos.esperaBloqueoMs("ANA@example.com"))
                .as("mayúsculas y espacios caen en la misma cuenta")
                .isEqualTo(MINUTO);
    }

    @Test
    void par_ventanaDeslizanteBloqueaAlSuperarElLimite() {
        IntentosLogin intentos = intentos();
        assertThat(intentos.registrarIntentoPar("1.1.1.1", "ana@example.com")).isZero();
        assertThat(intentos.registrarIntentoPar("1.1.1.1", "ana@example.com")).isZero();

        long espera = intentos.registrarIntentoPar("1.1.1.1", "ana@example.com");
        assertThat(espera).as("3º intento con límite 2/min → espera").isPositive();
        assertThat(espera).isLessThanOrEqualTo(MINUTO);
    }

    @Test
    void par_otraIpNoPenalizaLaMismaCuenta() {
        IntentosLogin intentos = intentos();
        intentos.registrarIntentoPar("1.1.1.1", "ana@example.com");
        intentos.registrarIntentoPar("1.1.1.1", "ana@example.com");
        intentos.registrarIntentoPar("1.1.1.1", "ana@example.com"); // agota la ventana

        assertThat(intentos.registrarIntentoPar("2.2.2.2", "ana@example.com"))
                .as("otra IP tiene SU ventana para el par")
                .isZero();
    }

    @Test
    void par_otraCuentaDetrasDeLaMismaIpNoPenaliza() {
        IntentosLogin intentos = intentos();
        intentos.registrarIntentoPar("1.1.1.1", "ana@example.com");
        intentos.registrarIntentoPar("1.1.1.1", "ana@example.com");
        intentos.registrarIntentoPar("1.1.1.1", "ana@example.com"); // agota la ventana de ana

        assertThat(intentos.registrarIntentoPar("1.1.1.1", "bob@example.com"))
                .as("una IP de NAT no consume el presupuesto de otras cuentas")
                .isZero();
    }

    @Test
    void par_liberarPlazaTrasPasarLaVentana() {
        IntentosLogin intentos = intentos();
        intentos.registrarIntentoPar("1.1.1.1", "ana@example.com");
        intentos.registrarIntentoPar("1.1.1.1", "ana@example.com");
        assertThat(intentos.registrarIntentoPar("1.1.1.1", "ana@example.com")).isPositive();

        ahoraMs += MINUTO + 1;
        assertThat(intentos.registrarIntentoPar("1.1.1.1", "ana@example.com"))
                .as("la ventana deslizante libera plazas al expirar las marcas")
                .isZero();
    }
}
