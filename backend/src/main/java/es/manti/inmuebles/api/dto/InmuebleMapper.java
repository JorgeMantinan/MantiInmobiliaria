package es.manti.inmuebles.api.dto;

import es.manti.inmuebles.application.ListadoInmuebles;
import es.manti.inmuebles.domain.Inmueble;
import es.manti.inmuebles.domain.InmuebleDatos;
import es.manti.inmuebles.domain.InmuebleResumen;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Mapeo entidad/proyección ↔ DTO del módulo inmuebles. Las entidades jamás
 * salen de aquí: la respuesta sólo contiene records de este paquete.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InmuebleMapper {

    InmuebleResponse toResponse(Inmueble inmueble);

    InmuebleSummary toSummary(InmuebleResumen resumen);

    List<InmuebleSummary> toSummaries(List<InmuebleResumen> resumenes);

    @Mapping(target = "content", source = "contenido")
    @Mapping(target = "nextCursor", source = "cursorSiguiente")
    InmueblePage toPage(ListadoInmuebles listado);

    InmuebleDatos toDatos(InmuebleRequest request);
}
