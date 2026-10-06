package es.manti.usuarios.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Alta de usuario email/password. */
@Schema(description = "Datos de registro de un usuario")
public record RegistroRequest(
        @NotBlank @Size(max = 120)
        @Schema(description = "Nombre visible", example = "Ana Pérez", maxLength = 120)
        String nombre,

        @NotBlank @Email @Size(max = 255)
        @Schema(description = "Email único", example = "ana@example.com", maxLength = 255)
        String email,

        @NotBlank @Size(min = 8, max = 72)
        @Schema(description = "Contraseña: 8-72 caracteres", example = "secreto123", minLength = 8, maxLength = 72)
        String password) {
}
