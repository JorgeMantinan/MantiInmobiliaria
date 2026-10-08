package es.manti.profesionales.api.dto;

/** Respuesta del PATCH de estado: {@code {id, estado}}. */
public record ProfesionalEstadoResponse(Long id, String estado) {
}
