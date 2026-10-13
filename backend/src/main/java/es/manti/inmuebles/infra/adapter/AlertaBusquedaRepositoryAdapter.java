package es.manti.inmuebles.infra.adapter;

import es.manti.inmuebles.domain.AlertaBusqueda;
import es.manti.inmuebles.domain.AlertaBusquedaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Adaptador JPA del puerto {@link AlertaBusquedaRepository}. */
@Repository
@RequiredArgsConstructor
public class AlertaBusquedaRepositoryAdapter implements AlertaBusquedaRepository {

    private final AlertaBusquedaJpaRepository jpaRepository;

    @Override
    public List<AlertaBusqueda> porUsuario(Long usuarioId) {
        return jpaRepository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId);
    }

    @Override
    public Optional<AlertaBusqueda> porIdYUsuario(Long id, Long usuarioId) {
        return jpaRepository.findByIdAndUsuarioId(id, usuarioId);
    }

    @Override
    public long cuantasPorUsuario(Long usuarioId) {
        return jpaRepository.countByUsuarioId(usuarioId);
    }

    @Override
    public List<AlertaBusqueda> activas() {
        return jpaRepository.findByActivaTrueOrderByIdAsc();
    }

    @Override
    public AlertaBusqueda guardar(AlertaBusqueda alerta) {
        return jpaRepository.save(alerta);
    }

    @Override
    public void eliminar(AlertaBusqueda alerta) {
        jpaRepository.delete(alerta);
    }
}
