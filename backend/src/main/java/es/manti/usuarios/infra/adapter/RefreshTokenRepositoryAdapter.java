package es.manti.usuarios.infra.adapter;

import es.manti.usuarios.domain.RefreshToken;
import es.manti.usuarios.domain.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Adaptador JPA del puerto {@link RefreshTokenRepository}. */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpaRepository;

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash);
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return jpaRepository.save(refreshToken);
    }

    @Override
    @Transactional
    public long revokeAllFromUsuario(Long usuarioId) {
        return jpaRepository.revocarTodosDeUsuario(usuarioId);
    }
}
