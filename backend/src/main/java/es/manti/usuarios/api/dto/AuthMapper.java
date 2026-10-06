package es.manti.usuarios.api.dto;

import es.manti.usuarios.application.AuthResult;
import es.manti.usuarios.domain.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Mapeador entidad ↔ DTO del módulo de autenticación (MapStruct, bean Spring).
 * La entidad nunca sale en un payload: sólo se usa en las firmas.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuthMapper {

    UserSummary toUserSummary(Usuario usuario);

    @Mapping(source = "usuario", target = "user")
    AuthResponse toResponse(AuthResult result);
}
