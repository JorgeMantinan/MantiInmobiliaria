package es.manti.usuarios.infra.adapter;

import es.manti.usuarios.domain.Usuario;
import es.manti.usuarios.domain.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Adaptador JPA del puerto {@link UsuarioRepository}. */
@Repository
@RequiredArgsConstructor
public class UsuarioRepositoryAdapter implements UsuarioRepository {

    private final UsuarioJpaRepository jpaRepository;

    @Override
    public Optional<Usuario> findByEmail(String email) {
        return jpaRepository.findByEmail(email);
    }

    @Override
    public Optional<Usuario> findByGoogleId(String googleId) {
        return jpaRepository.findByGoogleId(googleId);
    }

    @Override
    public Optional<Usuario> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public Usuario save(Usuario usuario) {
        return jpaRepository.save(usuario);
    }
}
