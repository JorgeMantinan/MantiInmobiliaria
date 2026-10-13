package es.manti.inmuebles.api.dto;

import es.manti.inmuebles.domain.AlertaBusqueda;
import es.manti.inmuebles.domain.AlertaDatos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Mapeo entidad ↔ DTO de las alertas (MapStruct con
 * {@code componentModel = "spring"}). La entidad jamás sale de
 * {@code domain}: sólo se devuelven records de este paquete.
 *
 * <p>{@link #toDatos} aplana el {@code filtros} anidado del request hacia el
 * record de dominio, y {@link #toFiltros} hace el recorrido inverso para la
 * respuesta — el contrato JSON siempre lleva el objeto {@code filtros}.</p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AlertaMapper {

    @Mapping(source = "filtros.estado", target = "estado")
    @Mapping(source = "filtros.tipo", target = "tipo")
    @Mapping(source = "filtros.municipio", target = "municipio")
    @Mapping(source = "filtros.precioMin", target = "precioMin")
    @Mapping(source = "filtros.precioMax", target = "precioMax")
    @Mapping(source = "filtros.habitacionesMin", target = "habitacionesMin")
    @Mapping(source = "filtros.destacado", target = "destacado")
    AlertaDatos toDatos(AlertaCreateRequest request);

    AlertaFiltros toFiltros(AlertaBusqueda alerta);

    /** {@code filtros} es anidado en la respuesta: se rellena con {@link #toFiltros}. */
    @Mapping(target = "filtros", source = "alerta")
    AlertaResponse toResponse(AlertaBusqueda alerta);

    List<AlertaResponse> toResponseList(List<AlertaBusqueda> alertas);
}
