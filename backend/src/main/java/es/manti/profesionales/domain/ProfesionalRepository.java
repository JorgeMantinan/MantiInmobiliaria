package es.manti.profesionales.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Puerto de persistencia de profesionales.
 */
public interface ProfesionalRepository {

    Profesional guardar(Profesional profesional);

    Optional<Profesional> porId(Long id);

    /** @return true si ya existe un profesional con ese email (409 en el registro). */
    boolean existeEmail(String email);

    /**
     * Profesionales ACTIVOS que comparten AL MENOS UN gremio de los pedidos
     * (ids ya resueltos por {@link GremioRepository#porSlugs}). La query es
     * {@code SELECT DISTINCT}: un profesional con tres gremios en común
     * devuelve UNA sola fila (clave para no duplicar filas del outbox).
     */
    List<Profesional> activosCompartiendoGremios(Collection<Long> gremioIds);

    /** Listado del backoffice, más recientes primero; {@code estado} null = todos. */
    List<Profesional> porEstado(EstadoProfesional estado);

    /**
     * Slugs de los gremios de cada profesional, en UNA sola query (anti-N+1):
     * el mapa sólo contiene los ids pedidos. Devuelto desde el adaptador para
     * que domain no conozca las filas cruzadas.
     */
    Map<Long, List<String>> slugsDeGremios(Collection<Long> profesionalIds);
}
