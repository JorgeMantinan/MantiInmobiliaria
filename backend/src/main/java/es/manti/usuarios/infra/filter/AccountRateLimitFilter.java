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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * payload). Si el email no se puede extraer, la petición sigue su curso: la
 * validación del controlador responde 400 igualmente.</p>
 */
@Slf4j
public class AccountRateLimitFilter extends OncePerRequestFilter {

    static final String RUTA_LOGIN = "/api/v1/auth/login";

    /** Un login real (&lt; 1 KiB) queda muy por debajo; el exceso es abuso. */
    static final int MAX_CUERPO_BYTES = 8_192;

    /**
     * Primera ocurrencia de {@code "email":"..."} del JSON. Los comillas y
     * barras dentro de un valor JSON válido SIEMPRE vienen escapadas
     * ({@code \"}), de modo que una password que contenga la cadena
     * <code>"email":"x"</code> no puede secuestrar la extracción.
     */
    private static final Pattern EMAIL_EN_JSON =
            Pattern.compile("\"email\"\\s*:\\s*\"([^\"]{1,255})\"");

    private final IntentosLogin intentos;
    private final LoginProteccionProperties properties;

    public AccountRateLimitFilter(IntentosLogin intentos, LoginProteccionProperties properties) {
        this.intentos = intentos;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.enabled() || !"POST".equals(request.getMethod())
                || !RUTA_LOGIN.equals(AuthRequestPath.normalizar(request))) {
            filterChain.doFilter(request, response);
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
        String email = extraerEmail(cuerpo);
        if (email == null) {
            // Sin email extraíble: la validación del controlador responderá
            // 400; no hay nada que proteger todavía.
            filterChain.doFilter(new CuerpoCacheado(request, cuerpo), response);
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

    private static String extraerEmail(byte[] cuerpo) {
        Matcher matcher = EMAIL_EN_JSON.matcher(new String(cuerpo, StandardCharsets.UTF_8));
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String tiempoLegible(long segundos) {
        if (segundos < 60) {
            return "unos segundos";
        }
        long minutos = (segundos + 59) / 60;
        return minutos <= 1 ? "un minuto" : minutos + " minutos";
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
