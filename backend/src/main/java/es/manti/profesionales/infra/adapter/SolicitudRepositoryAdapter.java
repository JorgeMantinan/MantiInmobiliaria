package es.manti.profesionales.infra.adapter;

import es.manti.profesionales.domain.Solicitud;
import es.manti.profesionales.domain.SolicitudRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Adaptador JPA del puerto {@link SolicitudRepository}. */
@Repository
@RequiredArgsConstructor
public class SolicitudRepositoryAdapter implements SolicitudRepository {

    private final SolicitudJpaRepository jpaRepository;

    @Override
    public Solicitud guardar(Solicitud solicitud) {
        return jpaRepository.save(solicitud);
    }

    @Override
    public Optional<Solicitud> porId(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Solicitud> ultimas(int limite) {
        return jpaRepository.ultimas(PageRequest.of(0, limite));
    }

    @Override
    public Map<Long, List<String>> slugsDeGremios(Collection<Long> solicitudIds) {
        if (solicitudIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> porSolicitud = new HashMap<>();
        for (Object[] fila : jpaRepository.slugsDeGremios(solicitudIds)) {
            porSolicitud.computeIfAbsent((Long) fila[0], id -> new ArrayList<>())
                    .add((String) fila[1]);
        }
        porSolicitud.values().forEach(lista -> lista.sort(Comparator.naturalOrder()));
        return porSolicitud;
    }
}
