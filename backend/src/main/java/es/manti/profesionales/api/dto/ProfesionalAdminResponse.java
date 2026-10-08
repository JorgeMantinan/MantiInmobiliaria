package es.manti.profesionales.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Fila del backoffice de profesionales (listado por estado). Los gremios van
 * como slugs ordenados alfabéticamente.
 */
public record ProfesionalAdminResponse(Long id, String nombre, String email, String telefono,
                                       String municipio, String estado, List<String> gremios,
                                       Instant creadoEn) {
}
