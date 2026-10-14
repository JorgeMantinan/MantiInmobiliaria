package es.manti.shared.config;

import es.manti.shared.error.PayloadTooLargeException;
import es.manti.shared.error.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Límite de tamaño del cuerpo de la petición (A04 — diseño inseguro), 256 KiB,
 * por DOS vías complementarias:
 *
 * <ol>
 *   <li><b>Declarado</b>: si la cabecera {@code Content-Length} supera el
 *       límite, se responde 413 (ProblemDetail {@code payload-too-large}) sin
 *       dejar que la petición siga por la cadena.</li>
 *   <li><b>Leído</b> — CIERRA EL HUECO CHUNKED: la petición se envuelve en
 *       {@link CuerpoContadoRequest}, cuyo stream <b>cuenta los bytes que se
 *       leen</b> y, al superar el límite, lanza
 *       {@link PayloadTooLargeException}, que {@code GlobalExceptionHandler}
 *       responde como el MISMO 413. Así también se limita un cuerpo que llega
 *       sin {@code Content-Length} (transfer-encoding chunked), que la vía 1
 *       no puede medir.</li>
 * </ol>
 *
 * <p>Exención: {@code Content-Type: multipart/*} pasa SIN envolver — el
 * futuro upload de fotos de inmuebles se servirá por aquí y el propio
 * multipart resolver de Spring ya acota el tamaño con
 * {@code spring.servlet.multipart.*}.</p>
 */
public class BodySizeLimitFilter extends OncePerRequestFilter {

    /** 256 KiB: cualquier payload real de la API queda muy por debajo. */
    public static final long MAXIMO_CUERPO_BYTES = 262_144L;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String contentLength = request.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLength != null && excede(contentLength)) {
            ProblemResponseWriter.write(response, HttpStatus.CONTENT_TOO_LARGE,
                    "payload-too-large", "Cuerpo demasiado grande",
                    "El cuerpo de la petición supera el máximo permitido (256 KiB)",
                    request.getRequestURI());
            return;
        }
        filterChain.doFilter(new CuerpoContadoRequest(request), response);
    }

    private static boolean excede(String contentLength) {
        try {
            return Long.parseLong(contentLength.trim()) > MAXIMO_CUERPO_BYTES;
        } catch (NumberFormatException e) {
            // Cabecera no numérica: se ignora aquí (Tomcat la rechaza antes).
            return false;
        }
    }

    /**
     * {@link HttpServletRequestWrapper} que envuelve el stream de entrada con
     * un contador de bytes: cualquier lector del cuerpo (Jackson en un
     * {@code @RequestBody}, el multipart resolver...) paga el conteo y recibe
     * {@link PayloadTooLargeException} al superar el límite.
     */
    private static final class CuerpoContadoRequest extends HttpServletRequestWrapper {

        private CuerpoContadoRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream original = super.getInputStream();
            return new ServletInputStream() {

                private long contado;

                @Override
                public int read() throws IOException {
                    int byteLeido = original.read();
                    if (byteLeido != -1) {
                        contar(1);
                    }
                    return byteLeido;
                }

                @Override
                public int read(byte[] buf, int off, int len) throws IOException {
                    int leidos = original.read(buf, off, len);
                    if (leidos > 0) {
                        contar(leidos);
                    }
                    return leidos;
                }

                private void contar(int bytes) {
                    contado += bytes;
                    if (contado > MAXIMO_CUERPO_BYTES) {
                        throw new PayloadTooLargeException();
                    }
                }

                @Override
                public boolean isFinished() {
                    return original.isFinished();
                }

                @Override
                public boolean isReady() {
                    return original.isReady();
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    original.setReadListener(readListener);
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            // El reader deriva del MISMO stream contado: no se escapa por aquí.
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null
                    ? StandardCharsets.ISO_8859_1
                    : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
