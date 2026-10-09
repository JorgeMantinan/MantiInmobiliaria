package es.manti.profesionales.application;

import es.manti.profesionales.domain.EstadoProfesional;

import java.time.Instant;
import java.util.List;

/**
 * Proyección ligera de un profesional para el backoffice. Se construye SIEMPRE
 * dentro de la transacción de lectura (evita {@code LazyInitializationException}
 * con {@code open-in-view: false}) y con los gremios resueltos en UNA query
 * (anti-N+1), de modo que el mapper de {@code api} sólo ve tipos planos.
 *
 * @param gremios slugs ordenados alfabéticamente
 */
public record ProfesionalResumen(Long id, String nombre, String email, String telefono,
                                 String municipio, EstadoProfesional estado, List<String> gremios,
                                 Instant creadoEn) {
}
