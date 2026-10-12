package es.manti.mercado.api.dto;

import es.manti.mercado.application.DatosMercado;
import es.manti.mercado.application.ResultadoImportacion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Mapeo application → api del módulo mercado. Ninguna entidad JPA sale de aquí.
 *
 * <p>{@code codigoIne} (Java) → {@code codigoINE} (JSON): el nombre del campo
 * público lo fija el contrato del ticket y MapStruct sólo empareja por nombre
 * exacto, así que el {@code @Mapping} es obligatorio.</p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MercadoMapper {

    @Mapping(target = "codigoINE", source = "codigoIne")
    MercadoResponse toResponse(DatosMercado datos);

    MercadoImportacionResponse toImportacion(ResultadoImportacion resultado);
}
