package es.manti.profesionales.api.dto;

import java.time.Instant;

/** Respuesta 201 del alta de una solicitud. */
public record SolicitudResponse(Long id, String estado, Instant creadoEn) {
}
