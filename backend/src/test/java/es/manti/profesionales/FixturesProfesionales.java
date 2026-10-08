package es.manti.profesionales;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Fixtures compartidas de los tests del módulo: profesionales, solicitudes y
 * conteos del outbox escritos directamente con SQL (los tests de la API sólo
 * necesitan las filas de partida, no volver a pasar por el controller).
 *
 * <p>Todas las fixtures usan emails {@code it-*} para poder limpiarse sin tocar
 * datos reales. NO es una clase de tests: no coincide con el patrón de
 * Surefire, así que no se ejecuta como tal.</p>
 */
final class FixturesProfesionales {

    /** Descripción válida (>= 10 caracteres) para el CHECK de la migración V4. */
    static final String DESCRIPCION = "Descripción de prueba lo bastante larga para la V4";

    private FixturesProfesionales() {
    }

    static long profesional(JdbcTemplate jdbc, String email, String estado, String... slugs) {
        Long id = jdbc.queryForObject("""
                INSERT INTO profesionales (nombre, email, estado)
                VALUES ('Profesional It', ?, ?)
                RETURNING id
                """, Long.class, email, estado);
        for (String slug : slugs) {
            jdbc.update("""
                    INSERT INTO profesional_gremio (profesional_id, gremio_id)
                    SELECT ?, id FROM gremios WHERE slug = ?
                    """, id, slug);
        }
        return id;
    }

    static long solicitud(JdbcTemplate jdbc, String emailContacto, String... slugs) {
        Long id = jdbc.queryForObject("""
                INSERT INTO solicitudes (nombre_contacto, email_contacto, municipio, descripcion)
                VALUES ('Contacto It', ?, 'It-Municipio', ?)
                RETURNING id
                """, Long.class, emailContacto, DESCRIPCION);
        for (String slug : slugs) {
            jdbc.update("""
                    INSERT INTO solicitud_gremio (solicitud_id, gremio_id)
                    SELECT ?, id FROM gremios WHERE slug = ?
                    """, id, slug);
        }
        return id;
    }

    /** Nº de filas del outbox de un canal para UNA solicitud. */
    static int contarOutbox(JdbcTemplate jdbc, long solicitudId, String canal) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notificaciones_outbox WHERE solicitud_id = ? AND canal = ?",
                Integer.class, solicitudId, canal);
        return total == null ? 0 : total;
    }

    /** Ids de los profesionales destinatarios del email de una solicitud. */
    static List<Long> destinatariosDeEmail(JdbcTemplate jdbc, long solicitudId) {
        return jdbc.queryForList(
                "SELECT destinatario_id FROM notificaciones_outbox "
                        + "WHERE solicitud_id = ? AND canal = 'EMAIL' ORDER BY destinatario_id",
                Long.class, solicitudId);
    }

    /** Estado global de una fila del outbox (para el despachador). */
    static String estadoOutbox(JdbcTemplate jdbc, long id) {
        return jdbc.queryForObject("SELECT estado FROM notificaciones_outbox WHERE id = ?",
                String.class, id);
    }

    static void limpiar(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM solicitudes WHERE email_contacto LIKE 'it-%@example.com'");
        jdbc.update("DELETE FROM profesionales WHERE email LIKE 'it-%@example.com'");
    }
}
