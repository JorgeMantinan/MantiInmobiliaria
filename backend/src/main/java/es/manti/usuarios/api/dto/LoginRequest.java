package es.manti.usuarios.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Login email/password. */
@Schema(description = "Credenciales de login")
public record LoginRequest(
        @NotBlank @Email @Size(max = 255)
        @Schema(description = "Email del usuario", example = "ana@example.com", maxLength = 255)
        String email,

        @NotBlank @Size(max = 72)
        @Schema(description = "Contraseña en claro", example = "secreto123", maxLength = 72)
        String password) {
}
