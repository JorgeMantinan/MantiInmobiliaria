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
}
