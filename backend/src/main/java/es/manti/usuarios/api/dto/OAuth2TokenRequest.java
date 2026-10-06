package es.manti.usuarios.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Canje del código de un solo uso recibido tras el callback de Google. */
@Schema(description = "Código de un solo uso devuelto por el callback de Google")
public record OAuth2TokenRequest(
        @NotBlank @Size(max = 255)
        @Schema(description = "Código opaco de un solo uso", example = "Y29kaWdvLW9wYXF1ZQ")
        String code) {
}
