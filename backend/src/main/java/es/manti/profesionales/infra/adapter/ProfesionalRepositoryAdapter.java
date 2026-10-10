package es.manti.profesionales.infra.adapter;

import es.manti.profesionales.domain.EstadoProfesional;
import es.manti.profesionales.domain.Profesional;
import es.manti.profesionales.domain.ProfesionalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptador JPA del puerto {@link ProfesionalRepository}.
 *
 * <p>{@link #slugsDeGremios} convierte las filas cruzadas (id, slug) en un
 * mapa agrupado: UNA query para toda la página del backoffice en lugar de una
 * query por profesional (anti-N+1, skill consultas-eficientes). Los slugs de
 * cada profesional quedan ordenados alfabéticamente para que la respuesta sea
 * estable entre llamadas.</p>
 */
@Repository
@RequiredArgsConstructor
public class ProfesionalRepositoryAdapter implements ProfesionalRepository {

    private final ProfesionalJpaRepository jpaRepository;

    /**
     * Guarda con FLUSH explícito (issue #51): el conflicto de versión
     * ({@code @Version} → {@code OptimisticLockingFailureException}) estalla
     * DENTRO del servicio y antes del log de éxito, de modo que una moderación
     * perdedora nunca se registra como si hubiera tenido efecto y puede
     * mapearse a 409 en el advice del módulo.
     */
    @Override
    public Profesional guardar(Profesional profesional) {
        return jpaRepository.saveAndFlush(profesional);
    }

    @Override
    public Optional<Profesional> porId(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public boolean existeEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public List<Profesional> activosCompartiendoGremios(Collection<Long> gremioIds) {
        if (gremioIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.activosCompartiendoGremios(EstadoProfesional.ACTIVO, gremioIds);
    }

    @Override
    public List<Profesional> porEstado(EstadoProfesional estado) {
        return estado == null
                ? jpaRepository.findAllByOrderByCreadoEnDesc()
                : jpaRepository.findByEstadoOrderByCreadoEnDesc(estado);
    }

    @Override
    public Map<Long, List<String>> slugsDeGremios(Collection<Long> profesionalIds) {
        if (profesionalIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> porProfesional = new HashMap<>();
        for (Object[] fila : jpaRepository.slugsDeGremios(profesionalIds)) {
            porProfesional.computeIfAbsent((Long) fila[0], id -> new ArrayList<>())
                    .add((String) fila[1]);
        }
        porProfesional.values().forEach(lista -> lista.sort(Comparator.naturalOrder()));
        return porProfesional;
    }
}
