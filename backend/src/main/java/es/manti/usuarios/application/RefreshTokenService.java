package es.manti.usuarios.application;

import es.manti.usuarios.domain.RefreshToken;
import es.manti.usuarios.domain.RefreshTokenRepository;
import es.manti.usuarios.domain.TokenInvalidoException;
import es.manti.usuarios.domain.Usuario;
import es.manti.usuarios.domain.UsuarioInactivoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh tokens opacos: emisión, validación con ROTACIÓN en cada uso y
 * revocación. En BD sólo se guarda el hash SHA-256; el valor en claro sólo
 * viaja en la cookie httpOnly. La fila anterior queda marcada como revocada,
 * lo que permite detectar reutilización (reuso ⇒ se revoca toda la sesión).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final int BYTES_TOKEN = 32;

    private final RefreshTokenRepository repository;
    private final AuthProperties properties;

    /** Emite un refresh token nuevo para el usuario. */
    @Transactional
    public String emitir(Usuario usuario) {
        String enClaro = generar();
        RefreshToken token = new RefreshToken();
        token.setTokenHash(sha256(enClaro));
        token.setUsuario(usuario);
        token.setExpiresAt(Instant.now().plus(properties.jwt().refreshTokenTtl()));
        token.setRevoked(false);
        repository.save(token);
        return enClaro;
    }

    /**
     * Valida el token presentado y lo rota: devuelve el usuario y deja el token
     * anterior invalidado (la siguiente petición emite uno nuevo con {@link #emitir}).
     */
    @Transactional
    public Usuario validarYRotar(String tokenEnClaro) {
        RefreshToken token = repository.findByTokenHash(sha256(tokenEnClaro))
                .orElseThrow(TokenInvalidoException::new);
        if (token.isRevoked()) {
            // Reutilización de un token ya rotado: posible robo, se cierra la
            // sesión completa. La revocación usa REQUIRES_NEW en el adaptador:
            // se persiste aunque esta transacción acabe en rollback por la
            // excepción que lanzamos justo después.
            Long usuarioId = token.getUsuario().getId();
            long revocados = repository.revokeAllFromUsuario(usuarioId);
            log.warn("Seguridad: reutilización de refresh token rotado detectada; "
                    + "sesión completa revocada para el usuario {} ({} tokens)", usuarioId, revocados);
            throw new TokenInvalidoException();
        }
        if (token.estaExpirado()) {
            throw new TokenInvalidoException();
        }
        Usuario usuario = token.getUsuario();
        if (!usuario.isActivo()) {
            throw new UsuarioInactivoException();
        }
        token.setRevoked(true);
        repository.save(token);
        return usuario;
    }

    /** Invalida el token presentado (logout). No distingue token inexistente de válido. */
    @Transactional
    public void revocar(String tokenEnClaro) {
        repository.findByTokenHash(sha256(tokenEnClaro)).ifPresent(token -> {
            token.setRevoked(true);
            repository.save(token);
        });
    }

    private static String generar() {
        byte[] bytes = new byte[BYTES_TOKEN];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
