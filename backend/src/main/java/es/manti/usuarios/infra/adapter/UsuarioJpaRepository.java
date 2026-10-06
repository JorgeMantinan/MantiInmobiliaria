package es.manti.usuarios.infra.adapter;

import es.manti.usuarios.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Repositorio Spring Data JPA de la tabla {@code usuarios}. */
public interface UsuarioJpaRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByGoogleId(String googleId);

    boolean existsByEmail(String email);
}
