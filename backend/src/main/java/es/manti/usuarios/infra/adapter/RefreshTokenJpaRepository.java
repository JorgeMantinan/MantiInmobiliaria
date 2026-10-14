package es.manti.usuarios.infra.adapter;

import es.manti.usuarios.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Repositorio Spring Data JPA de la tabla {@code refresh_tokens}. */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Issue #31 — {@code JOIN FETCH} del usuario: {@code validarYRotar} lo lee
     * SIEMPRE (id, activo, email), así que el {@code @ManyToOne(LAZY)} era un
     * segundo SELECT en cada refresh; con el fetch, un refresh = UN SELECT.
     * Sin él, además, el usuario llegaba como proxy que sólo se resolvía dentro
     * de la transacción.
     */
    @Query("SELECT t FROM RefreshToken t JOIN FETCH t.usuario WHERE t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Issue #31 — un UPDATE por JPQL NO pasa por {@code @UpdateTimestamp} de la
     * entidad (Hibernate sólo lo dispara al persistir entidades), así que sin
     * esto la revocación masiva dejaba {@code updated_at} congelado en la fecha
     * del login. {@code CURRENT_TIMESTAMP} usa el reloj del SERVIDOR, el mismo
     * que escribe el resto de columnas.
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true, t.updatedAt = CURRENT_TIMESTAMP "
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
