package es.manti.usuarios.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #8 — purga programada de {@code refresh_tokens} caducados.
 *
 * <p>La tabla crece una fila por login/rotación con TTL de 7 días y sin
 * consumidor del índice {@code idx_refresh_tokens_expires_at}. La purga borra
 * sólo lo caducado hace más de 30 días (retención para auditar reuso).</p>
 *
 * <p>En el perfil de test el job está desactivado
 * ({@code manti.purgas.enabled=false}), así que aquí se invoca la acción a mano
 * y se comprueba además que el bean del job no existe.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenPurgaTest {

    private static final String HASH_DE_PRUEBA = "hash-de-prueba-sin-usar";

    @Autowired
    private RefreshTokenPurgaService purgas;

    @Autowired
    private ObjectProvider<RefreshTokenPurgaJob> job;

    @Autowired
    private JdbcTemplate jdbc;

    private String email;

    @BeforeEach
    void crearUsuarioDePrueba() {
        email = "it-purga-" + UUID.randomUUID() + "@example.com";
        // password_hash debe ser NO NULL: ck_usuarios_credencial exige que
        // exista password_hash o google_id (este usuario nunca hace login).
        jdbc.update("INSERT INTO usuarios (email, nombre, password_hash, rol, activo) "
                        + "VALUES (?, ?, ?, 'USER', true)", email, "Purga Test", HASH_DE_PRUEBA);
    }

    @AfterEach
    void limpiar() {
        // Los refresh_tokens del usuario caen en cascada (fk ON DELETE CASCADE)
        jdbc.update("DELETE FROM usuarios WHERE email = ?", email);
    }

    @Test
    void purgar_borraSoloLosTokensExpiradosHaceMasDe30Dias() {
        String muyViejo = insertarToken("muy-viejo", Instant.now().minus(Duration.ofDays(40)));
        String recienExpirado = insertarToken("recien-expirado", Instant.now().minus(Duration.ofDays(10)));
        String vigente = insertarToken("vigente", Instant.now().plus(Duration.ofDays(5)));

        int borrados = purgas.purgar();

        assertThat(borrados).as("alguna fila antigua debe borrarse").isGreaterThanOrEqualTo(1);
        assertThat(contar(muyViejo)).as("expirado hace 40 días → borrado").isZero();
        assertThat(contar(recienExpirado)).as("expirado hace 10 días → se conserva").isEqualTo(1);
        assertThat(contar(vigente)).as("vigente → se conserva").isEqualTo(1);
    }

    /** Segunda pasada: idempotente y sin tocar nada de lo que queda. */
    @Test
    void purgar_repetida_noTocaLosTokensConservados() {
        String recienExpirado = insertarToken("recien-expirado-2", Instant.now().minus(Duration.ofDays(10)));

        purgas.purgar();
        purgas.purgar();

        assertThat(contar(recienExpirado)).isEqualTo(1);
    }

    /** El perfil de test desactiva el job (manti.purgas.enabled=false). */
    @Test
    void job_noSeCrea_enElPerfilDeTest() {
        assertThat(job.getIfAvailable())
                .as("RefreshTokenPurgaJob no debe existir con manti.purgas.enabled=false")
                .isNull();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String insertarToken(String marca, Instant expira) {
        String tokenHash = (marca + "-" + UUID.randomUUID()).replace("-", "");
        tokenHash = (tokenHash + tokenHash).substring(0, 64);
        jdbc.update("""
                        INSERT INTO refresh_tokens (token_hash, usuario_id, expires_at, revoked)
                        VALUES (?, (SELECT id FROM usuarios WHERE email = ?), ?::timestamptz, false)
                        """,
                tokenHash, email, expira.toString());
        return tokenHash;
    }

    private int contar(String tokenHash) {
        return jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token_hash = ?",
                Integer.class, tokenHash);
    }
}
