package es.manti.usuarios.infra.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import es.manti.shared.error.ProblemResponseWriter;
import es.manti.usuarios.application.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiting en memoria por IP (sin dependencias externas) para las rutas
 * sensibles: login/registro (60 s), solicitudes de servicio y registro de
 * profesionales (1 hora), forgot/reset de contraseña (1 hora), refresh y canje
 * OAuth2 de Google (60 s, issue #17), listado público de inmuebles (60 s,
 * issue #22), swipes/deck/favoritos (60 s, comentario del issue #17) y las
 * escrituras del CRUD de alertas (POST/PATCH/DELETE, 30/min, follow-up #98).
 * Cada ruta lleva SU ventana, configurable con {@code auth.rate-limit.*}.
 *
 * <p>La respuesta 429 lleva la cabecera {@code Retry-After} con los segundos
 * que faltan para liberar la primera plaza de la ventana (redondeo al alza).</p>
 *
 * <p><b>Almacenamiento (issue #32).</b> El mapa de ventanas es una caché
 * Caffeine con {@code maximumSize} acotado y {@code expireAfterWrite} de la
 * ventana MÁXIMA (1 hora): así Caffeine hace la expulsión en O(1) amortizado y
 * desaparece la limpieza O(n) que recorría el mapa entero cada vez que crecía.
 * La vida de la entrada nunca puede ser menor que la ventana de su ruta, de
 * modo que una marca viva no se pierde jamás (si expirara a los 60 s, una ruta
 * "por hora" se convertiría en "por minuto": bastaría esperar 61 s para
 * repetir).</p>
 *
 * <p>Nota despliegue: la IP se toma de {@code remoteAddr}; si el backend va
 * detrás de un reverse proxy hay que activar
 * {@code server.forward-headers-strategy} para que la IP real llegue en
 * {@code X-Forwarded-For}. OJO: el proxy debe SOBRESCRIBIR el header (Vercel
 * lo hace); si lo añadiera a los existentes, un atacante inyectando
 * {@code X-Forwarded-For} ganaría ventana nueva por petición — en ese caso
 * fijar el header en el proxy o {@code FORWARD_HEADERS_STRATEGY=none}.
 * Límite best-effort pensado para una sola instancia.</p>
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    /** Ventana fija de las rutas de "por minuto". */
    private static final long VENTANA_MINUTO_MS = 60_000L;

    /** Ventana de las rutas de la FASE 5 (límites "por hora"). */
    private static final long VENTANA_HORA_MS = 3_600_000L;

    /** Tamaño máximo del mapa de ventanas (una entrada por ruta + IP). */
    private static final int MAX_ENTRADAS = 100_000;

    private final AuthProperties properties;
    private final Cache<String, Marcas> ventanas;

    /** Límite de una ruta concreta: nº de peticiones y anchura de su ventana. */
    private record Limite(int peticiones, long ventanaMs) {
    }

    /**
     * Marcas de tiempo de una clave (ruta + IP).
     *
     * <p>Sin {@code equals()} a propósito (identidad): en cada petición se
     * devuelve una envoltura NUEVA con el mismo {@code Deque} mutado. Así
     * Caffeine ve una escritura distinta, renueva el {@code expireAfterWrite}
     * y la ventana deslizante no se cierra antes de tiempo; si se devolviera la
     * misma instancia el TTL no se refrescaría.</p>
     */
    private static final class Marcas {
        private final Deque<Long> marcas;

        private Marcas(Deque<Long> marcas) {
            this.marcas = marcas;
        }

        private Deque<Long> marcas() {
            return marcas;
        }
    }

    public AuthRateLimitFilter(AuthProperties properties) {
        this.properties = properties;
        this.ventanas = Caffeine.newBuilder()
                .maximumSize(MAX_ENTRADAS)
                // Vida = ventana MÁXIMA (1 hora): véase el javadoc de la clase.
                .expireAfterWrite(Duration.ofMillis(VENTANA_HORA_MS))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ruta = AuthRequestPath.normalizar(request);
        Limite limite = limitePara(request.getMethod(), ruta);
        if (limite == null) {
            filterChain.doFilter(request, response);
            return;
        }
        // La clave usa la ruta normalizada: si usara la cruda, cada
        // variante ";x=1", "%6C..." tendría su propia ventana y se evade el límite.
        // Además el id de ruta se colapsa a {id}: si no, PATCH/DELETE
        // /api/v1/alertas/7 y /8 tendrían ventanas INDEPENDIENTES (N ids = N
        // ventanas = límite inexistente) y cada id sería una entrada del mapa.
        String clave = ruta.replaceFirst("/\\d+$", "/{id}") + '|' + ipDe(request);
        long esperaMs = msDeEspera(clave, limite);
        if (esperaMs > 0) {
            // Segundos enteros hasta liberar la primera plaza de la ventana
            // (redondeo al alza, mínimo 1): RFC 9110, Retry-After en segundos.
            long segundos = Math.max(1, (esperaMs + 999) / 1000);
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(segundos));
            ProblemResponseWriter.write(response, HttpStatus.TOO_MANY_REQUESTS,
                    "rate-limit", "Demasiadas peticiones",
                    "Límite de peticiones alcanzado; inténtelo de nuevo en " + espera(limite),
                    ruta);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Limite limitePara(String metodo, String rutaNormalizada) {
        AuthProperties.RateLimit rateLimit = properties.rateLimit();
        // follow-up #98 — alertas: las tres escrituras del CRUD de alertas
        // (POST crear, PATCH editar, DELETE borrar) limitadas por IP: sin
        // límite, una sesión podría encolar trabajo del job en bucle (cada
        // alerta evalúa en cada pasada) o machacar el outbox con avisos.
        if (esEscrituraDeAlertas(metodo, rutaNormalizada)) {
            return new Limite(rateLimit.alertasPerMinute(), VENTANA_MINUTO_MS);
        }
        if ("GET".equals(metodo)) {
            return switch (rutaNormalizada) {
                // issue #22 — listado PÚBLICO: sin límite un cliente anónimo
                // puede forzar COUNT + ILIKE trgm en cada petición. El detalle
                // (/api/v1/inmuebles/{id}) queda fuera: lookup por PK, barato.
                case "/api/v1/inmuebles" ->
                        new Limite(rateLimit.inmueblesPerMinute(), VENTANA_MINUTO_MS);
                // Comentario del issue #17 — deck y favoritos: lecturas baratas
                // pero autenticadas, acotadas para que un token robado no las
                // dispare sin freno.
                case "/api/v1/swipes/deck" ->
                        new Limite(rateLimit.deckPerMinute(), VENTANA_MINUTO_MS);
                case "/api/v1/swipes/favoritos" ->
                        new Limite(rateLimit.favoritosPerMinute(), VENTANA_MINUTO_MS);
                default -> null;
            };
        }
        if (!"POST".equals(metodo)) {
            return null;
        }
        return switch (rutaNormalizada) {
            case "/api/v1/auth/login" -> new Limite(rateLimit.loginPerMinute(), VENTANA_MINUTO_MS);
            case "/api/v1/auth/register" ->
                    new Limite(rateLimit.registerPerMinute(), VENTANA_MINUTO_MS);
            // issue #17 — el refresh rota el token en cada uso: el bootstrap
            // multi-pestaña amplía las escrituras y un intento de fuerza bruta
            // sobre la cookie sólo se frena por IP (la ventana es de 60 s para
            // no cortar una ráfaga legítima de pestañas al abrir la app).
            case "/api/v1/auth/refresh" ->
                    new Limite(rateLimit.refreshPerMinute(), VENTANA_MINUTO_MS);
            // issue #17 — canje del código de Google: cada login legítimo hace
            // UNA petición; limitar por IP acota el bombardeo con códigos
            // caducados, que provocaría un intercambio contra Google por cada
            // intento.
            case "/api/v1/auth/oauth2/token" ->
                    new Limite(rateLimit.oauth2TokenPerMinute(), VENTANA_MINUTO_MS);
            // Comentario del issue #17 — escritura barata, acotada por la
            // constraint uq_swipes_usuario_inmueble, pero sin límite un token
            // robado podría saturar la tabla de swipes.
            case "/api/v1/swipes" ->
                    new Limite(rateLimit.swipesPerMinute(), VENTANA_MINUTO_MS);
            // FASE 5 — rutas PÚBLICAS: sin límite el outbox y el alta de
            // profesionales serían un denial-of-service económico.
            case "/api/v1/solicitudes" ->
                    new Limite(rateLimit.solicitudesPerHour(), VENTANA_HORA_MS);
            case "/api/v1/profesionales/registro" ->
                    new Limite(rateLimit.registroProfesionalPerHour(), VENTANA_HORA_MS);
            // issue #57 — reset de contraseña: además de la ventana de 15 min
            // del token, la IP sólo puede pedir 5 enlaces/hora y canjear 10
            // tokens/hora (evita agujerear el envío de emails y la fuerza
            // bruta sobre los enlaces).
            case "/api/v1/auth/password/forgot" ->
                    new Limite(rateLimit.forgotPasswordPerHour(), VENTANA_HORA_MS);
            case "/api/v1/auth/password/reset" ->
                    new Limite(rateLimit.resetPasswordPerHour(), VENTANA_HORA_MS);
            default -> null;
        };
    }

    /** POST /api/v1/alertas y PATCH/DELETE /api/v1/alertas/{id}. */
    private static boolean esEscrituraDeAlertas(String metodo, String rutaNormalizada) {
        return ("POST".equals(metodo) && "/api/v1/alertas".equals(rutaNormalizada))
                || (("PATCH".equals(metodo) || "DELETE".equals(metodo))
                && rutaNormalizada.startsWith("/api/v1/alertas/"));
    }

    private static String espera(Limite limite) {
        return limite.ventanaMs() == VENTANA_HORA_MS ? "una hora" : "un minuto";
    }

    /**
     * Devuelve {@code 0} si la petición cabe en la ventana (y la registra);
     * si no, los milisegundos que faltan para que expire la marca más antigua
     * de la ventana (lo que tarda en liberarse la primera plaza), que es lo
     * que se refleja en la cabecera {@code Retry-After} de la 429.
     *
     * <p>La mutación de la clave es atómica vía {@code Cache.asMap().compute}:
     * Caffeine garantiza la exclusión mutua por clave, así que no hace falta
     * bloquear el {@code Deque} a mano (antes era un {@code synchronized} por
     * ventana).</p>
     */
    private long msDeEspera(String clave, Limite limite) {
        AtomicLong esperaMs = new AtomicLong();
        ventanas.asMap().compute(clave, (k, previas) -> {
            long ahora = System.currentTimeMillis();
            Deque<Long> marcas = previas == null ? new ArrayDeque<>() : previas.marcas();
            while (!marcas.isEmpty() && marcas.peekFirst() < ahora - limite.ventanaMs()) {
                marcas.pollFirst();
            }
            if (marcas.size() < limite.peticiones()) {
                marcas.addLast(ahora);
                esperaMs.set(0L);
            } else {
                Long primeraMarca = marcas.peekFirst();
                // Límite 0 (o ventana vacía tras expulsar la entrada): no hay
                // marca de la que restar — se espera la ventana entera en vez
                // de fallar con NPE.
                esperaMs.set(primeraMarca == null
                        ? limite.ventanaMs()
                        : primeraMarca + limite.ventanaMs() - ahora);
            }
            return new Marcas(marcas);
        });
        return esperaMs.get();
    }

    static String ipDe(HttpServletRequest request) {
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
