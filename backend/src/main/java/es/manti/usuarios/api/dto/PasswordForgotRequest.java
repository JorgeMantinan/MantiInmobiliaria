package es.manti.usuarios.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Solicitud del enlace de restablecimiento de contraseña (olvidé mi password). */
@Schema(description = "Solicitud de restablecimiento de contraseña por email")
public record PasswordForgotRequest(
        @NotBlank @Email @Size(max = 255)
        @Schema(description = "Email de la cuenta", example = "ana@example.com", maxLength = 255)
        String email) {
}
