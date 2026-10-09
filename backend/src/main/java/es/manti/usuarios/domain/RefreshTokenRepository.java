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

    /**
     * Borra los refresh tokens expirados con más de {@code diasRetencion}
     * días: la tabla crece una fila por login/refresh y hay que purgarla
     * (issue #8). Sólo toca filas caducadas desde hace tanto tiempo, así que
     * conserva las recientes para auditar reuso y aprovecha el índice
     * {@code idx_refresh_tokens_expires_at}.
     *
     * @param diasRetencion días de retención tras la caducidad (30 en el job)
     * @return número de filas borradas
     */
    int purgarExpirados(int diasRetencion);
}
