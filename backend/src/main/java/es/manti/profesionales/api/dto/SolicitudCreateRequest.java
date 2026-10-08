package es.manti.profesionales.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Alta de una solicitud de servicio desde el portal (anónimo).
 *
 * @param gremios 1..8 slugs del catálogo; se comprueba además que existan
 *                (400 {@code errors:[{field: "gremios"}]})
 */
public record SolicitudCreateRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 120, message = "no puede superar 120 caracteres")
        String nombreContacto,

        @NotBlank(message = "es obligatorio")
        @Email(message = "debe ser un email válido")
        @Size(max = 255, message = "no puede superar 255 caracteres")
        String emailContacto,

        @Size(max = 30, message = "no puede superar 30 caracteres")
        String telefono,

        @NotBlank(message = "es obligatorio")
        @Size(max = 120, message = "no puede superar 120 caracteres")
        String municipio,

        @NotBlank(message = "es obligatorio")
        @Size(min = 10, max = 2000, message = "debe tener entre 10 y 2000 caracteres")
        String descripcion,

        @NotEmpty(message = "debe indicar al menos un gremio")
        @Size(max = 8, message = "no puede superar 8 gremios")
        List<@NotBlank(message = "no puede estar vacío")
        @Size(max = 40, message = "no puede superar 40 caracteres") String> gremios) {
}
