package es.manti.usuarios.infra.adapter;

import es.manti.usuarios.domain.PasswordComprometidoPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Adaptador de Have I Been Pwned (issue #10) con el protocolo de
 * <b>k-anonymity</b>: sólo se envía el prefijo de 5 caracteres del hash SHA-1
 * de la contraseña; el servidor devuelve todos los sufijos de ese rango y la
 * comprobación se hace EN LOCAL. La contraseña en claro (y su hash completo)
 * jamás sale de la máquina, y no hace falta API key.
 *
 * <p><b>Timeouts cortos (2 s conexión / 3 s lectura).</b> Es una llamada
 * síncrona dentro del registro/restablecimiento: si HIBP tarda, se corta
 * rápido.</p>
 *
 * <p><b>Fail open.</b> Si HIBP no responde (429, timeout, DNS...), se deja
 * pasar la contraseña con un {@code log.warn}: la disponibilidad del registro
 * no puede depender de un tercero. Sólo un TRUE del servicio bloquea.</p>
 *
 * <p>Nota A02: el SHA-1 de aquí es el PROTOCOLO k-anonymity de HIBP, no un
 * hashing de credenciales — las contraseñas se siguen guardando con BCrypt.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "auth.hibp", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class HibpPasswordAdapter implements PasswordComprometidoPort {

    /** Endpoint público de rangos; sin credenciales y sin parámetros de usuario. */
    static final String URL_RANGO = "https://api.pwnedpasswords.com/range/";

    static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(2);

    static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(3);

    private final RestClient restClient;

    public HibpPasswordAdapter() {
        this(construirCliente());
    }

    /** Variante con cliente inyectable (tests con {@code MockRestServiceServer}). */
    HibpPasswordAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public boolean estaComprometido(String password) {
        String hash = sha1Mayusculas(password);
        String prefijo = hash.substring(0, 5);
        String sufijo = hash.substring(5);
        try {
            String cuerpo = restClient.get().uri(URL_RANGO + prefijo).retrieve().body(String.class);
            return contieneSufijo(cuerpo, sufijo);
        } catch (RestClientException e) {
            log.warn("HIBP no responde ({}); se permite el uso de la contraseña (fail open)",
                    e.getMessage());
            return false;
        }
    }

    static RestClient construirCliente() {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(TIMEOUT_CONEXION, TIMEOUT_LECTURA);
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .defaultHeader(HttpHeaders.USER_AGENT, "MantiInmobiliaria")
                .build();
    }

    /**
     * Formato del cuerpo: una línea por hash del rango,
     * {@code SUFIJO_MAYUSCULA:CONTADOR} (p. ej.
     * {@code 61E4C9B9...FD8:36812747}).
     */
    static boolean contieneSufijo(String cuerpo, String sufijo) {
        if (cuerpo == null || sufijo == null) {
            return false;
        }
        return cuerpo.lines()
                .map(String::trim)
                .filter(linea -> !linea.isEmpty())
                .anyMatch(linea -> {
                    int separador = linea.indexOf(':');
                    return separador > 0 && sufijo.equalsIgnoreCase(linea.substring(0, separador));
                });
    }

    /** SHA-1 en hex mayúsculas: formato exacto que espera el protocolo de HIBP. */
    static String sha1Mayusculas(String password) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().withUpperCase()
                    .formatHex(sha1.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 no disponible", e);
        }
    }
}
