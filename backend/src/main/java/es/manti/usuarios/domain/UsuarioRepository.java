package es.manti.usuarios.domain;

import java.util.Optional;

/**
 * Puerto de persistencia de usuarios. Implementado en infra por Spring Data JPA.
 */
public interface UsuarioRepository {

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByGoogleId(String googleId);

    Optional<Usuario> findById(Long id);

    boolean existsByEmail(String email);

    Usuario save(Usuario usuario);
}
