package es.manti.profesionales.infra.adapter;

import es.manti.profesionales.domain.SolicitudIdempotencia;
import es.manti.profesionales.domain.SolicitudIdempotenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Adaptador JPA del puerto {@link SolicitudIdempotenciaRepository}. */
@Repository
@RequiredArgsConstructor
public class SolicitudIdempotenciaRepositoryAdapter implements SolicitudIdempotenciaRepository {

    private final SolicitudIdempotenciaJpaRepository jpaRepository;

    @Override
    public Optional<SolicitudIdempotencia> porClave(String clave) {
        return jpaRepository.findById(clave);
    }

    @Override
    public SolicitudIdempotencia guardar(SolicitudIdempotencia registro) {
        return jpaRepository.save(registro);
    }
}
