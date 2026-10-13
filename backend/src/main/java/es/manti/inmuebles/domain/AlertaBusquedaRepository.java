package es.manti.inmuebles.domain;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia de las alertas de búsqueda. Implementado en infra por
 * Spring Data JPA (mismo patrón que {@link InmuebleRepository}).
 */
public interface AlertaBusquedaRepository {

    /** Alertas del usuario, de más reciente a más antigua. */
    List<AlertaBusqueda> porUsuario(Long usuarioId);

    /** Alerta SÓLO si es del usuario actual (owner-scoped; si no, 404). */
    Optional<AlertaBusqueda> porIdYUsuario(Long id, Long usuarioId);

    /** Número de alertas del usuario (límite anti-abuso del alta). */
    long cuantasPorUsuario(Long usuarioId);

    /** Alertas ACTIVAS, ordenadas por id: las únicas que evalúa el job. */
    List<AlertaBusqueda> activas();

    AlertaBusqueda guardar(AlertaBusqueda alerta);

    void eliminar(AlertaBusqueda alerta);
}
