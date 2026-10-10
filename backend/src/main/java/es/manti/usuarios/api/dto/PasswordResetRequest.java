package es.manti.usuarios.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Canje del enlace de restablecimiento: token opaco + contraseña nueva. */
@Schema(description = "Restablecimiento de contraseña con el token recibido por email")
public record PasswordResetRequest(
        @NotBlank @Size(max = 128)
        @Schema(description = "Token opaco de un solo uso incluido en el enlace del email",
                example = "dGVzdC10b2tlbi1kZS11bmUtc3Vzby11c28", maxLength = 128)
        String token,

        @NotBlank @Size(min = 8, max = 72)
        @Schema(description = "Contraseña: 8-72 caracteres (mismas reglas que el registro)",
                example = "secreto123", minLength = 8, maxLength = 72)
        String password) {
}
