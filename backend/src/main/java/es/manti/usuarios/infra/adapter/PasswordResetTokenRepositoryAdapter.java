package es.manti.usuarios.infra.adapter;

import es.manti.usuarios.domain.PasswordResetToken;
import es.manti.usuarios.domain.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/** Adaptador JPA del puerto {@link PasswordResetTokenRepository}. */
@Repository
@RequiredArgsConstructor
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository jpaRepository;

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return jpaRepository.buscarPorTokenHash(tokenHash);
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        return jpaRepository.save(token);
    }

    @Override
    public int consumir(Long id, Instant ahora) {
        return jpaRepository.consumir(id, ahora);
    }

    @Override
    public boolean existeVigentePara(Long usuarioId, Instant ahora) {
        return jpaRepository.existsByUsuarioIdAndUsadoEnIsNullAndExpiresAtAfter(usuarioId, ahora);
    }

    @Override
    public int eliminarTodosDeUsuario(Long usuarioId) {
        return jpaRepository.eliminarTodosDeUsuario(usuarioId);
    }
}
