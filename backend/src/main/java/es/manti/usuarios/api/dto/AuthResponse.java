package es.manti.usuarios.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta de login/registro/refresh: el access token va en el body y el
 * refresh token en cookie httpOnly (path {@code /api/v1/auth}), nunca en el body.
 */
@Schema(description = "Sesión emitida: access token en body + refresh token en cookie httpOnly")
public record AuthResponse(
        @Schema(description = "Access token JWT (HS256, 15 min)", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
        @Schema(description = "Segundos hasta la expiración del access token", example = "900")
        long expiresIn,
        @Schema(description = "Usuario autenticado")
        UsuarioSummary user) {
}
