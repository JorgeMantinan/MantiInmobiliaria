package es.manti.inmuebles.domain;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

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

    /**
     * Menor marca de agua entre las alertas ACTIVAS: vacío si no hay ninguna.
     * Es el CORTE del barrido del job — sólo tiene sentido evaluar si el mayor
     * id de inmuebles la supera (follow-up #98).
     */
    OptionalLong minimaMarcaActiva();

    /**
     * Lote del barrido del job: alertas ACTIVAS con {@code id > desdeId}, en
     * orden ASC y bloqueadas {@code FOR UPDATE SKIP LOCKED} (multi-instancia:
     * si otra instancia ya ha cogido una fila, esta query no espera ni la
     * devuelve — mismo criterio que {@code pendientes} del outbox).
     */
    List<AlertaBusqueda> activasDesde(long desdeId, int limite);

    AlertaBusqueda guardar(AlertaBusqueda alerta);

    void eliminar(AlertaBusqueda alerta);
}
