package es.manti.profesionales.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Registro PÚBLICO de un profesional. Siempre nace {@code PENDIENTE}: un
 * administrador debe activarlo.
 *
 * @param gremios 1..8 slugs ofertados; además deben existir (400)
 */
public record ProfesionalRegistroRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 120, message = "no puede superar 120 caracteres")
        String nombre,

        @NotBlank(message = "es obligatorio")
        @Email(message = "debe ser un email válido")
        @Size(max = 255, message = "no puede superar 255 caracteres")
        String email,

        @Size(max = 30, message = "no puede superar 30 caracteres")
        String telefono,

        @Size(max = 120, message = "no puede superar 120 caracteres")
        String municipio,

        @NotEmpty(message = "debe indicar al menos un gremio")
        @Size(max = 8, message = "no puede superar 8 gremios")
        List<@NotBlank(message = "no puede estar vacío")
        @Size(max = 40, message = "no puede superar 40 caracteres") String> gremios) {
}
