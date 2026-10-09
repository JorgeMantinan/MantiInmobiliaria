package es.manti.profesionales.application;

import java.util.List;

/**
 * Datos de registro público de un profesional (queda PENDIENTE hasta que un
 * ADMIN lo active).
 *
 * @param gremios slugs ofertados, validados por Jakarta en el controller
 *                (1..8, no vacíos)
 */
public record RegistroProfesionalCommand(String nombre, String email, String telefono,
                                         String municipio, List<String> gremios) {
}
