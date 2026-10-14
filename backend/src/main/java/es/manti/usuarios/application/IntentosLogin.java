package es.manti.usuarios.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Registro en memoria de los intentos fallidos de login (issue #10) y de la
 * ventana por par IP+email (issue #5), respaldado por cachés Caffeine con
 * tamaño acotado — mismo patrón que {@code AuthRateLimitFilter} y
 * {@code ConteoInmueblesCache}: sin dependencias externas y best-effort para
 * una sola instancia.
 *
 * <p><b>Backoff exponencial por cuenta</b>: tras alcanzar el umbral de
 * {@code auth.login.fallos-antes-de-backoff} fallos, CADA fallo adicional
 * bloquea la cuenta durante {@code backoff-inicial * factor^n} (1, 2, 4, 8...
 * minutos por defecto) hasta el tope. El bloqueo se mide contra el reloj del
 * propio registro (inyectable en tests, igual que el Ticker del filtro de rate
 * limit). Un acierto ({@link #registrarAcierto}) invalida la entrada.</p>
 *
 * <p><b>Ventana por par</b>: la clave es {@code sha256(ip + '|' + email)}
 * —nunca se guarda el email en claro en la caché— y admite
 * {@code pares-por-minuto} intentos en una ventana deslizante de 60 s. Así un
 * atacante distribuido por muchas IPs sigue topándose con el backoff por
 * cuenta, y una IP de NAT no consume el presupuesto de las demás cuentas.</p>
 */
@Slf4j
@Service
public class IntentosLogin {

    /** Ventana deslizante de la dimensión por par IP+email. */
    private static final long VENTANA_PAR_MS = 60_000L;

    /** Vida de las entradas de pares (una hora amortigua picos de 60 s). */
    private static final long VIDA_PAR_MS = 3_600_000L;

    private static final int MAX_ENTRADAS = 100_000;

    private final LoginProteccionProperties properties;
    private final LongSupplier reloj;
    private final Cache<String, EstadoFallos> fallos;
    private final Cache<String, Marcas> pares;

    /** Estado del contador de fallos de una cuenta. */
    private record EstadoFallos(int fallos, long bloqueadoHastaMs) {
    }

    /** Marcas de tiempo del par (envoltura de identidad, como en el filtro de IP). */
    private static final class Marcas {
        private final Deque<Long> marcas;

        private Marcas(Deque<Long> marcas) {
            this.marcas = marcas;
        }

        private Deque<Long> marcas() {
            return marcas;
        }
    }

    /** @Autowired explícito: hay un segundo constructor (con reloj) para tests. */
    @Autowired
    public IntentosLogin(LoginProteccionProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    /**
     * Variante con reloj inyectable (tests de backoff sin esperar minutos):
     * el MISMO reloj gobierna el bloqueo y la expiración de las cachés
     * Caffeine (derivado a {@code Ticker} de nanosegundos).
     */
    IntentosLogin(LoginProteccionProperties properties, LongSupplier reloj) {
        this.properties = properties;
        this.reloj = reloj;
        Ticker ticker = () -> TimeUnit.MILLISECONDS.toNanos(reloj.getAsLong());
        // Vida de la entrada de fallos: un poco MAYOR que el tope del backoff
        // para que un bloqueo nunca se pierda por expiración con la cuenta
        // todavía caliente.
        this.fallos = Caffeine.newBuilder()
                .maximumSize(MAX_ENTRADAS)
                .expireAfterWrite(properties.ventanaFallos())
                .ticker(ticker)
                .build();
        this.pares = Caffeine.newBuilder()
                .maximumSize(MAX_ENTRADAS)
                .expireAfterWrite(Duration.ofMillis(VIDA_PAR_MS))
                .ticker(ticker)
                .build();
    }

    /**
     * @return milisegundos que la cuenta sigue bloqueada por backoff, o
     *         {@code 0} si no hay bloqueo activo
     */
    public long esperaBloqueoMs(String email) {
        EstadoFallos estado = fallos.getIfPresent(normalizarEmail(email));
        if (estado == null) {
            return 0L;
        }
        long ahora = reloj.getAsLong();
        return estado.bloqueadoHastaMs() > ahora ? estado.bloqueadoHastaMs() - ahora : 0L;
    }

    /**
     * Registra UN intento del par IP+email en su ventana deslizante de 60 s.
     *
     * @return {@code 0} si el intento cabe en la ventana; en caso contrario,
     *         los milisegundos hasta liberar la primera plaza (para el
     *         {@code Retry-After} de la 429)
     */
    public long registrarIntentoPar(String ip, String email) {
        String clave = sha256(ip + '|' + normalizarEmail(email));
        AtomicLong esperaMs = new AtomicLong();
        pares.asMap().compute(clave, (k, previas) -> {
            long ahora = reloj.getAsLong();
            Deque<Long> marcas = previas == null ? new ArrayDeque<>() : previas.marcas();
            while (!marcas.isEmpty() && marcas.peekFirst() < ahora - VENTANA_PAR_MS) {
                marcas.pollFirst();
            }
            if (marcas.size() < properties.paresPorMinuto()) {
                marcas.addLast(ahora);
                esperaMs.set(0L);
            } else {
                Long primera = marcas.peekFirst();
                esperaMs.set(primera == null ? VENTANA_PAR_MS : primera + VENTANA_PAR_MS - ahora);
            }
            return new Marcas(marcas);
        });
        return Math.max(0L, esperaMs.get());
    }

    /** Suma un fallo y calcula (o alarga) el bloqueo exponencial de la cuenta. */
    public void registrarFallo(String email) {
        String clave = normalizarEmail(email);
        fallos.asMap().compute(clave, (k, previo) -> {
            int acumulados = (previo == null ? 0 : previo.fallos()) + 1;
            long bloqueadoHasta = bloqueadoHastaMs(acumulados);
            if (bloqueadoHasta > 0) {
                log.debug("Login bloqueado para la cuenta tras {} fallo(s) ({} ms)",
                        acumulados, bloqueadoHasta - reloj.getAsLong());
            }
            return new EstadoFallos(acumulados, bloqueadoHasta);
        });
    }

    /** Login correcto: el contador de la cuenta vuelve a cero. */
    public void registrarAcierto(String email) {
        fallos.invalidate(normalizarEmail(email));
    }

    /**
     * Duración del bloqueo tras {@code acumulados} fallos: 0 por debajo del
     * umbral; {@code backoff-inicial * factor^(acumulados - umbral)} al
     * alcanzarlo, con tope. Cálculo en {@code long} acotado al tope en cada
     * paso para no desbordar con exponentes grandes.
     */
    private long bloqueadoHastaMs(int acumulados) {
        int umbral = properties.fallosAntesDeBackoff();
        if (acumulados < umbral) {
            return 0L;
        }
        long inicialMs = properties.backoffInicial().toMillis();
        long topeMs = properties.backoffTope().toMillis();
        long factor = Math.max(1, properties.factorBackoff());
        long duracion = inicialMs;
        for (int i = 0; i < acumulados - umbral && duracion < topeMs; i++) {
            duracion = Math.min(duracion * factor, topeMs);
        }
        return reloj.getAsLong() + Math.min(duracion, topeMs);
    }

    private static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** SHA-256 en hex: la caché nunca contiene el email en claro. */
    private static String sha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
