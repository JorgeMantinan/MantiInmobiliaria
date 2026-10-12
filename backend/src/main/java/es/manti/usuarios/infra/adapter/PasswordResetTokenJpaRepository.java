package es.manti.usuarios.infra.adapter;

import es.manti.usuarios.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/** Repositorio Spring Data JPA de la tabla {@code password_reset_tokens}. */
public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Hash → token con el usuario YA resuelto ({@code JOIN FETCH}): el puerto
     * se usa fuera de transacción y la asociación es perezosa, así que un
     * simple {@code findByTokenHash} dejaría un proxy inaccesible.
     */
    @Query("SELECT t FROM PasswordResetToken t JOIN FETCH t.usuario "
            + "WHERE t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> buscarPorTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Canje atómico de UN solo uso (issue #57): sólo la primera petición que
     * pase los tres predicados rellena {@code usado_en}; las concurrentes
     * ven 0 filas y caen en 400.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usadoEn = :ahora "
            + "WHERE t.id = :id AND t.usadoEn IS NULL AND t.expiresAt > :ahora")
    int consumir(@Param("id") Long id, @Param("ahora") Instant ahora);

    /** ¿Hay un token sin canjear y sin caducar para este usuario? (cooldown) */
    boolean existsByUsuarioIdAndUsadoEnIsNullAndExpiresAtAfter(Long usuarioId, Instant ahora);

    /** Borra todos los tokens del usuario en UNA sentencia (al emitir uno nuevo). */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.usuario.id = :usuarioId")
    int eliminarTodosDeUsuario(@Param("usuarioId") Long usuarioId);
}
