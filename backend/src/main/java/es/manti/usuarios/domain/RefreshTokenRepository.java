package es.manti.usuarios.domain;

import java.util.Optional;

/**
 * Puerto de persistencia de refresh tokens. Implementado en infra por Spring Data JPA.
 */
public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken refreshToken);

    /**
     * Revoca todos los tokens vigentes de un usuario
     * (se usa al detectar reutilización de un token ya rotado).
     *
     * @return número de tokens revocados
     */
    long revokeAllFromUsuario(Long usuarioId);
}
