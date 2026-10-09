package es.manti.profesionales.api.dto;

import es.manti.profesionales.application.ProfesionalResumen;
import es.manti.profesionales.application.RegistroProfesionalCommand;
import es.manti.profesionales.domain.Profesional;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Mapeador de profesionales (MapStruct, bean Spring). La entidad sólo aparece
 * en las firmas de los métodos de respuesta puntuales (registro y cambio de
 * estado), que no tocan su colección perezosa de gremios.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProfesionalMapper {

    RegistroProfesionalCommand toCommand(ProfesionalRegistroRequest request);

    ProfesionalEstadoResponse toRegistroResponse(Profesional profesional);

    ProfesionalEstadoResponse toEstadoResponse(Profesional profesional);

    ProfesionalAdminResponse toAdminResponse(ProfesionalResumen resumen);

    List<ProfesionalAdminResponse> toAdminResponseList(List<ProfesionalResumen> resumenes);
}
