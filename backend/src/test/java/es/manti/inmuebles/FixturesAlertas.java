package es.manti.inmuebles;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

/**
 * Fixtures compartidas de los tests de alertas: usuarios, alertas e inmuebles
 * escritos directamente con SQL (los tests de evaluación sólo necesitan las
 * filas de partida, sin pasar por el controller).
 *
 * <p>Todas usan prefijos {@code it-alerta-} / {@code It-alerta-} para poder
 * limpiarse sin tocar datos reales, y el {@code AfterEach} de cada test borra
 * el usuario — alertas y avisos caen en cascada por las FK de V13.</p>
 *
 * <p>NO es una clase de tests: no coincide con el patrón de Surefire, así que
 * no se ejecuta como tal.</p>
 */
final class FixturesAlertas {

    private FixturesAlertas() {
    }

    /** Usuario USER activo (el CHECK de credenciales se cumple con password_hash). */
    static long usuario(JdbcTemplate jdbc, String email) {
        return jdbc.queryForObject("""
                INSERT INTO usuarios (email, password_hash, nombre, rol, activo)
                VALUES (?, 'hash-de-prueba', 'It-Usuario Alerta', 'USER', true)
                RETURNING id
                """, Long.class, email);
    }

    /**
     * Alerta ACTIVA con los filtros mínimos (estado VENTA + municipio opcional
     * + precio máximo) y la marca de agua indicada.
     */
    static long alerta(JdbcTemplate jdbc, long usuarioId, String nombre, String municipio,
                       BigDecimal precioMax, long marcaDeAgua) {
        return jdbc.queryForObject("""
                INSERT INTO alertas_busqueda
                    (usuario_id, nombre, estado, municipio, precio_max,
                     ultimo_inmueble_avisado_id, activa)
                VALUES (?, ?, 'VENTA', ?, ?, ?, true)
                RETURNING id
                """, Long.class, usuarioId, nombre, municipio, precioMax, marcaDeAgua);
    }

    /** Inmueble VENTA/PISO de prueba en el municipio indicado. */
    static long inmueble(JdbcTemplate jdbc, String municipio, BigDecimal precio) {
        return jdbc.queryForObject("""
                INSERT INTO inmuebles (titulo, precio, tipo, estado, direccion, municipio,
                                       provincia, lat, lng)
                VALUES (?, ?, 'PISO', 'VENTA', 'Calle It 1', ?, 'Madrid', 40.4, -3.7)
                RETURNING id
                """, Long.class, "It-piso de prueba", precio, municipio);
    }

    /** Mayor id de inmuebles en este momento (la marca de agua del alta). */
    static long maximoIdInmuebles(JdbcTemplate jdbc) {
        Long maximo = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM inmuebles", Long.class);
        return maximo == null ? 0L : maximo;
    }

    /** Borra las fixtures de alertas; usuario y avisos caen en cascada. */
    static void limpiar(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-alerta-%@example.com'");
        jdbc.update("DELETE FROM inmuebles WHERE municipio LIKE 'It-alerta-%'");
    }
}
