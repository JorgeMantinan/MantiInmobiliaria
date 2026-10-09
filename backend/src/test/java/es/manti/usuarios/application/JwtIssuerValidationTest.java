package es.manti.usuarios.application;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import es.manti.usuarios.domain.Rol;
import es.manti.usuarios.domain.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validación de issuer del decoder ({@link JwtService}): un token firmado con
 * NUESTRA clave HS256 pero con {@code iss} de OTRA "app" NO autentica. Es el
 * escenario de hardening pre-release: la clave es compartida por despliegue,
 * así que el issuer es lo que impide que otra aplicación (o un emisor de
 * tests/pentest) fabrique tokens válidos con la misma clave.
 */
@SpringBootTest
@ActiveProfiles("test")
class JwtIssuerValidationTest {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthProperties properties;

    @Test
    void decoder_rechazaTokenConIssuerDeOtraApp() throws Exception {
        String token = tokenFirmadoConNuestraClave("otra-app");

        assertThatThrownBy(() -> jwtService.decoder().decode(token))
                .as("iss distinto de 'manti-backend' debe rechazarse aunque la firma sea válida")
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    void decoder_aceptaTokenConNuestroIssuer() throws Exception {
        Jwt jwt = jwtService.decoder().decode(tokenFirmadoConNuestraClave("manti-backend"));

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("manti-backend");
        assertThat(jwt.getSubject()).isEqualTo("1");
    }

    @Test
    void encoder_emiteTokensQuePasaElDecoder() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail("ana@example.com");
        usuario.setRol(Rol.USER);

        Jwt jwt = jwtService.decoder().decode(jwtService.crearAccessToken(usuario));

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("manti-backend");
        assertThat(jwt.getSubject()).isEqualTo("1");
    }

    /** Firma HS256 con la MISMA clave de {@code auth.jwt.secret}, issuer a elegir. */
    private String tokenFirmadoConNuestraClave(String issuer) throws Exception {
        Date ahora = new Date();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("1")
                .issueTime(ahora)
                .expirationTime(new Date(ahora.getTime() + 300_000))
                .jwtID(UUID.randomUUID().toString())
                .claim("email", "ana@example.com")
                .claim("rol", "USER")
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        byte[] clave = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        token.sign(new MACSigner(clave));
        return token.serialize();
    }
}
