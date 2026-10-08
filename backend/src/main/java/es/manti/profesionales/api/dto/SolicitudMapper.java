package es.manti.profesionales.api.dto;

import es.manti.profesionales.application.NuevaSolicitudCommand;
import es.manti.profesionales.application.SolicitudResumen;
import es.manti.profesionales.domain.Solicitud;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Mapeador de solicitudes (MapStruct, bean Spring). Los enums del dominio se
 * serializan como su nombre ({@code NUEVA}), que es el contrato de la API; la
 * entidad sólo aparece en las firmas, nunca en un payload.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SolicitudMapper {

    NuevaSolicitudCommand toCommand(SolicitudCreateRequest request);

    SolicitudResponse toResponse(Solicitud solicitud);

    SolicitudAdminResponse toAdminResponse(SolicitudResumen resumen);

    List<SolicitudAdminResponse> toAdminResponseList(List<SolicitudResumen> resumenes);
}
