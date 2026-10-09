package es.manti.profesionales.application;

import es.manti.profesionales.domain.EstadoSolicitud;

import java.time.Instant;
import java.util.List;

/**
 * Proyección ligera de una solicitud para el backoffice (mismas razones que
 * {@link ProfesionalResumen}: transacción propia, sin entidades perezosas).
 *
 * @param gremios slugs pedidos, ordenados alfabéticamente
 */
public record SolicitudResumen(Long id, String nombreContacto, String emailContacto,
                               String telefono, String municipio, String descripcion,
                               EstadoSolicitud estado, List<String> gremios, Instant creadoEn) {
}
