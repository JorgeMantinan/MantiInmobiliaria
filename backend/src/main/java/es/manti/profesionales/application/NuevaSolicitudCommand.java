package es.manti.profesionales.application;

import java.util.List;

/**
 * Datos de alta de una solicitud de servicio. Comando de aplicación: la capa
 * {@code application} no conoce los DTOs de {@code api} (regla ArchUnit).
 *
 * @param gremios slugs pedidos, ya validados por Jakarta en el controller
 *                (1..8, no vacíos)
 */
public record NuevaSolicitudCommand(String nombreContacto, String emailContacto, String telefono,
                                    String municipio, String descripcion, List<String> gremios) {
}
