package es.manti.profesionales.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Fila del backoffice de solicitudes (últimas 50; MVP sin paginación —
 * documentado como TODO en la spec).
 */
public record SolicitudAdminResponse(Long id, String nombreContacto, String emailContacto,
                                     String telefono, String municipio, String descripcion,
                                     String estado, List<String> gremios, Instant creadoEn) {
}
