package es.manti.usuarios.application;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import es.manti.usuarios.domain.Usuario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Emisión y validación de access tokens JWT HS256.
 *
 * Librería: <b>Nimbus JOSE + JWT vía spring-security-oauth2-jose</b>
 * (traído por {@code spring-boot-starter-security-oauth2-resource-server}).
 * Justificación: misma librería que usa Spring Security internamente (no añade
 * dependencias nuevas ni conflictos de versiones), soporte nativo de HS256 con
 * {@code ImmutableSecret}, validación de expiración incluida y camino estándar
 * para el resource server; jjwt habría añadido una segunda librería JWT.
 */
@Slf4j
@Service
public class JwtService {

    private static final int CLAVE_MINIMA_BYTES = 32;

    /** Issuer de los access tokens: lo firma al emitir y lo EXIGE el decoder
     *  ({@link JwtValidators#createDefaultWithIssuer}), de modo que un token
     *  firmado con la misma clave pero emitido por otra "app" no valida. */
    private static final String ISSUER = "manti-backend";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AuthProperties properties;

    public JwtService(AuthProperties properties) {
        this.properties = properties;
        String secret = properties.jwt() != null ? properties.jwt().secret() : null;
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Falta auth.jwt.secret (variable de entorno JWT_SECRET)");
        }
        byte[] clave = secret.getBytes(StandardCharsets.UTF_8);
        if (clave.length < CLAVE_MINIMA_BYTES) {
            throw new IllegalStateException(
                    "auth.jwt.secret (JWT_SECRET) debe tener al menos " + CLAVE_MINIMA_BYTES + " caracteres");
        }
        SecretKeySpec key = new SecretKeySpec(clave, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        // Spring Security 7: el builder de clave simétrica no expone
        // .validator(...), así que se fija tras construirlo. Valida expiración
        // (default) Y el issuer: un token con la misma clave firmado por otra
        // "app" no autentica.
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        this.decoder = jwtDecoder;
        log.info("JWT HS256 inicializado (TTL access {}s)", properties.jwt().accessTokenTtl().toSeconds());
    }

    /**
     * Genera el access token con los claims fijados en el ticket:
     * {@code sub} = id de usuario, {@code email} y {@code rol}.
     */
    public String crearAccessToken(Usuario usuario) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(usuario.getId()))
                // jti único: dos tokens emitidos en el mismo segundo nunca son idénticos
                // (firma HS256 determinista sobre claims idénticos).
                .id(UUID.randomUUID().toString())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(properties.jwt().accessTokenTtl()))
                .claim("email", usuario.getEmail())
                .claim("rol", usuario.getRol().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Segundos de validez del access token (claim {@code exp} y campo {@code expiresIn} de la API). */
    public long segundosDeExpiracion() {
        return properties.jwt().accessTokenTtl().toSeconds();
    }

    /** Decoder usado por el resource server y por los tests para verificar claims. */
    public JwtDecoder decoder() {
        return decoder;
    }
}
