package es.manti.profesionales.application;

import es.manti.profesionales.domain.Solicitud;

/**
 * Resultado del alta de una solicitud: la entidad creada (o la original, en el
 * replay de una clave de idempotencia) y el código HTTP que debe devolver la
 * API (el de la respuesta original).
 *
 * @param solicitud solicitud a serializar en la respuesta
 * @param codigo    código HTTP de la respuesta original (201 en el alta)
 */
public record AltaSolicitud(Solicitud solicitud, int codigo) {
}
