package es.manti.usuarios.infra.filter;

import es.manti.shared.error.ProblemResponseWriter;
import es.manti.usuarios.application.IntentosLogin;
import es.manti.usuarios.application.LoginProteccionProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Segunda y tercera capa de protección del LOGIN, complementarias al rate
 * limit por IP de {@link AuthRateLimitFilter} (issues #10 y #5). Sólo actúa
 * sobre {@code POST /api/v1/auth/login}:
 *
 * <ol>
 *   <li><b>Backoff por cuenta</b> (#10): si la cuenta está bloqueada por
 *       fallos consecutivos, responde 429 con {@code Retry-After} sin llegar
 *       a tocar BCrypt. Tras el filtro, un 401 de la respuesta suma fallo y un
 *       2xx resetea el contador (vía {@link IntentosLogin}).</li>
 *   <li><b>Ventana por par sha256(IP + email)</b> (#5): un atacante con
 *       muchas IPs no puede concentrar la fuerza bruta en UNA cuenta sin
 *       coste, y una IP de NAT no consume el presupuesto de otras cuentas del
 *       mismo origen.</li>
 * </ol>
 *
 * <p>La IP se toma de {@code remoteAddr}, ya corregida por
 * {@code ForwardedHeaderFilter} cuando {@code server.forward-headers-strategy}
 * está activo (perfil prod, Vercel sobrescribe X-Forwarded-For) — mismo
 * supuesto documentado en {@link AuthRateLimitFilter}.</p>
 *
 * <p>El email se extrae del cuerpo JSON con un tope de lectura de 8 KiB: un
 * login real ocupa &lt; 1 KiB, así que un cuerpo mayor se rechaza con 400
 * antes de gastar memoria (y no puede evadir la protección rellenando el
 * payload). La extracción usa el MISMO {@code ObjectMapper} que
 * {@code @RequestBody}: escapes unicode del JSON y claves duplicadas
 * resuelven exactamente igual que en el controlador, de modo que la vista del
 * filtro y la del login coinciden siempre (revisión de seguridad PR #102: una
 * regex sobre el crudo se evadía con la clave "email" escrita como
 * {@code em} + {@code il} vía escape, o con claves duplicadas).
 * JSON ilegible o campo email ausente/no textual → 400 desde el filtro: el
 * login no puede autenticar sin email, así que no se pierde comportamiento.</p>
 */
@Slf4j
public class AccountRateLimitFilter extends OncePerRequestFilter {

    static final String RUTA_LOGIN = "/api/v1/auth/login";

    /** Un login real (&lt; 1 KiB) queda muy por debajo; el exceso es abuso. */
    static final int MAX_CUERPO_BYTES = 8_192;

    private final IntentosLogin intentos;
    private final LoginProteccionProperties properties;
    private final ObjectMapper objectMapper;

    public AccountRateLimitFilter(IntentosLogin intentos, LoginProteccionProperties properties,
                                  ObjectMapper objectMapper) {
        this.intentos = intentos;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.enabled() || !"POST".equals(request.getMethod())
                || !RUTA_LOGIN.equals(AuthRequestPath.normalizar(request))) {
            filterChain.doFilter(request, response);
            return;
        }
        // RFC 8259: JSON se intercambia SIEMPRE en UTF-8. Un charset distinto
        // en el Content-Type haría que el filtro (UTF-8 fijo) y el converter
        // de Spring (honra el charset) vieran strings distintos — divergencia
        // que evadiría la ventana por par con emails no-ASCII (revisión
        // seguridad PR #102). Sólo se admite UTF-8 o ausente (default).
        if (!esUtf8(request.getContentType())) {
            ProblemResponseWriter.write(response, HttpStatus.BAD_REQUEST,
                    "cuerpo-invalido", "Cuerpo de la petición inválido",
                    "El Content-Type del login debe ser application/json (UTF-8)",
                    request.getRequestURI());
            return;
        }
        byte[] cuerpo = leerCuerpoAcotado(request);
        if (cuerpo == null) {
            // Cuerpo ilegible o demasiado grande para un login: 400 sin llegar
            // al controlador (el cuerpo YA se consumió; reenviarlo truncado
            // produciría un JSON malformado peor que un rechazo limpio).
            ProblemResponseWriter.write(response, HttpStatus.BAD_REQUEST,
                    "cuerpo-invalido", "Cuerpo de la petición inválido",
                    "El cuerpo de un login no puede superar " + MAX_CUERPO_BYTES + " bytes",
                    request.getRequestURI());
            return;
        }
        String email;
        try {
            JsonNode nodo = objectMapper.readTree(cuerpo);
            JsonNode emailNode = nodo == null ? null : nodo.get("email");
            email = (emailNode != null && emailNode.isString()) ? emailNode.asString() : null;
        } catch (RuntimeException e) {
            // Jackson 3 lanza excepciones unchecked: JSON ilegible.
            log.debug("Login con JSON ilegible en el filtro de cuenta: {}", e.getMessage());
            email = null;
        }
        if (email == null || email.isBlank()) {
            // Sin email canónico: el login no puede autenticar (el
            // controlador respondería 400 por validación) y no hay cuenta que
            // proteger ni contabilizar.
            ProblemResponseWriter.write(response, HttpStatus.BAD_REQUEST,
                    "validacion-fallida", "Validación fallida",
                    "El campo email es obligatorio",
                    request.getRequestURI());
            return;
        }
        long esperaCuenta = intentos.esperaBloqueoMs(email);
        if (esperaCuenta > 0) {
            rechazar(response, request, esperaCuenta, "login-bloqueado",
                    "Demasiados intentos fallidos de acceso");
            return;
        }
        long esperaPar = intentos.registrarIntentoPar(AuthRateLimitFilter.ipDe(request), email);
        if (esperaPar > 0) {
            rechazar(response, request, esperaPar, "rate-limit",
                    "Límite de intentos de acceso");
            return;
        }
        boolean falloDeCadena = false;
        try {
            filterChain.doFilter(new CuerpoCacheado(request, cuerpo), response);
        } catch (ServletException | IOException | RuntimeException e) {
            // Excepción fuera del control del login: NO se observa el estado
            // (podría quedar un 200 provisional en memoria) y se propaga.
            falloDeCadena = true;
            throw e;
        } finally {
            // El resultado del login se observa en la respuesta: 401 suma
            // fallo (backoff); <400 resetea el contador de la cuenta.
            if (!falloDeCadena) {
                int estado = response.getStatus();
                if (estado == HttpStatus.UNAUTHORIZED.value()) {
                    intentos.registrarFallo(email);
                } else if (estado < HttpStatus.BAD_REQUEST.value()) {
                    intentos.registrarAcierto(email);
                }
            }
        }
    }

    private void rechazar(HttpServletResponse response, HttpServletRequest request,
                          long esperaMs, String typeSlug, String titulo) throws IOException {
        // RFC 9110: Retry-After en segundos enteros, redondeo al alza (mín. 1)
        long segundos = Math.max(1, (esperaMs + 999) / 1000);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(segundos));
        ProblemResponseWriter.write(response, HttpStatus.TOO_MANY_REQUESTS, typeSlug, titulo,
                titulo + "; inténtelo de nuevo en " + tiempoLegible(segundos),
                request.getRequestURI());
    }

    /**
     * Lee el cuerpo hasta {@code MAX_CUERPO_BYTES}; devuelve {@code null} si
     * no se puede leer completo (payload abusivo para un login o stream
     * roto). El stream no se cierra: lo gestiona el contenedor.
     */
    private byte[] leerCuerpoAcotado(HttpServletRequest request) {
        try {
            InputStream entrada = request.getInputStream();
            byte[] leidos = entrada.readNBytes(MAX_CUERPO_BYTES + 1);
            return leidos.length > MAX_CUERPO_BYTES ? null : leidos;
        } catch (IOException e) {
            log.debug("Login con cuerpo ilegible ({}); se deja pasar al controlador", e.getMessage());
            return null;
        }
    }

    private static String tiempoLegible(long segundos) {
        if (segundos < 60) {
            return "unos segundos";
        }
        long minutos = (segundos + 59) / 60;
        return minutos <= 1 ? "un minuto" : minutos + " minutos";
    }

    /** {@code Content-Type} sin charset o con UTF-8 (RFC 8259). */
    private static boolean esUtf8(String contentType) {
        if (contentType == null) {
            return true;
        }
        for (String parte : contentType.split(";")) {
            String limpia = parte.trim();
            if (limpia.toLowerCase(java.util.Locale.ROOT).startsWith("charset=")) {
                String charset = limpia.substring("charset=".length()).trim();
                return "utf-8".equalsIgnoreCase(charset) || "utf8".equalsIgnoreCase(charset);
            }
        }
        return true;
    }

    /**
     * Sirve el cuerpo YA leído por el filtro: el resto de la cadena (Jackson
     * en {@code @RequestBody}) lo relee del array en caché sin tocar la red.
     */
    private static final class CuerpoCacheado extends HttpServletRequestWrapper {

        private final byte[] cuerpo;

        private CuerpoCacheado(HttpServletRequest request, byte[] cuerpo) {
            super(request);
            this.cuerpo = cuerpo;
        }

        @Override
        public ServletInputStream getInputStream() {
            // ByteArrayInputStream no declara IOException en read/available.
            ByteArrayInputStream flujo = new ByteArrayInputStream(cuerpo);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return flujo.read();
                }

                @Override
                public int read(byte[] buf, int off, int len) {
                    return flujo.read(buf, off, len);
                }

                @Override
                public boolean isFinished() {
                    return flujo.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("lectura asíncrona no soportada");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null
                    ? StandardCharsets.ISO_8859_1
                    : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
