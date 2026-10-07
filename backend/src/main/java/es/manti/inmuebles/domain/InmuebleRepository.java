package es.manti.inmuebles.domain;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia de inmuebles. Implementado en infra por Spring Data JPA
 * (patrón idéntico al de usuarios: {@code UsuarioRepository} + adaptador).
 */
public interface InmuebleRepository {

    /**
     * Página keyset del listado (SIN offset): siempre la primera del orden dado,
     * acotada por el cursor.
     *
     * @param filtros filtros opcionales (nulos = sin filtrar)
     * @param orden   orden pedido (define ORDER BY y predicado del cursor)
     * @param cursor  última fila de la página anterior; null en la primera página
     * @param limite  máximo de filas a devolver (el servicio pide {@code limite + 1})
     * @return filas proyectadas, sin entidades
     */
    List<InmuebleResumen> listar(InmuebleFiltros filtros, InmuebleOrden orden, InmuebleCursor cursor, int limite);

    /** Total de inmuebles que cumplen los filtros (sin cursor), para {@code totalElements}. */
    long contar(InmuebleFiltros filtros);

    /** Detalle: sólo inmuebles vigentes (no eliminados). Lo usa la API pública. */
    Optional<Inmueble> porId(Long id);

    /**
     * Detalle SIN filtrar el soft-delete: lo usa el backoffice para distinguir
     * un inmueble inexistente (404) de uno ya eliminado (409).
     */
    Optional<Inmueble> porIdIncluyendoEliminados(Long id);

    Inmueble guardar(Inmueble inmueble);
}
