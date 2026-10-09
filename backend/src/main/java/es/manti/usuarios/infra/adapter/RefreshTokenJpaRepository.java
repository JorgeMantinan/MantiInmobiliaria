package es.manti.usuarios.infra.adapter;

import es.manti.usuarios.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Repositorio Spring Data JPA de la tabla {@code refresh_tokens}. */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true "
            + "WHERE t.usuario.id = :usuarioId AND t.revoked = false")
    long revocarTodosDeUsuario(@Param("usuarioId") Long usuarioId);

    /**
     * Purga diaria (issue #8): borra los caducados hace más de
     * {@code dias} días. Nativa para usar {@code now()} del servidor (el mismo
     * reloj que escribe {@code expires_at}) y recorrer
     * {@code idx_refresh_tokens_expires_at}.
     */
    @Modifying
    @Query(value = "DELETE FROM refresh_tokens "
            + "WHERE expires_at < now() - make_interval(days => :dias)", nativeQuery = true)
    int purgarExpirados(@Param("dias") int dias);
}
