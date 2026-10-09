package es.manti.profesionales.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cambio de estado de un profesional desde el backoffice. Se valida con
 * {@code @Pattern} (y no con un enum) para que un valor no admitido devuelva
 * 400 con {@code errors:[{field: "estado", ...}]} y no un error genérico de
 * JSON.
 */
public record ProfesionalEstadoRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "ACTIVO|RECHAZADO", message = "sólo se admiten los valores ACTIVO o RECHAZADO")
        String estado) {
}
