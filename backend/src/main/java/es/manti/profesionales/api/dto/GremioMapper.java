package es.manti.profesionales.api.dto;

import es.manti.profesionales.domain.Gremio;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/** Mapeador del catálogo de gremios (MapStruct, bean Spring). */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GremioMapper {

    List<GremioResponse> toResponseList(List<Gremio> gremios);
}
