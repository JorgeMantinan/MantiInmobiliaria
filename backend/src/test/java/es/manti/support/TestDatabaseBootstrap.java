package es.manti.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Infraestructura de tests (issue #28). Asegura de forma idempotente la BD
 * dedicada de la suite (manti_test) y su rol propio ANTES de que el
 * contexto Spring arranque Hikari y Flyway: JDBC no permite conectarse a una
 * BD inexistente, así que la suite la crea contra la BD de mantenimiento
 * (postgres) con el usuario admin del docker-compose (manti/manti_dev
 * por defecto, sobreescribible con TEST_ADMIN_*).
 *
 * <p>Garantías de seguridad:
 * <ul>
 *   <li>Sólo actúa con el perfil test activo (lo decide el caller).</li>
 *   <li>Jamás crea ni toca las bases protegidas (postgres, templates y
 *       la BD dev manti).</li>
 *   <li>TestDatabaseIsolationTest verifica además, con una query de
 *       control, que la conexión activa de la suite es manti_test.</li>
 * </ul>
 */
public final class TestDatabaseBootstrap {

    /** Bases que el bootstrap de tests JAMÁS crea ni toca. */
    private static final Set<String> BASES_PROTEGIDAS =
            Set.of("postgres", "template0", "template1", "manti");

    /** Claves ya aseguradas en esta JVM (una sola ronda de DDL por suite). */
    private static final Set<String> ASEGURADAS = ConcurrentHashMap.newKeySet();

    private TestDatabaseBootstrap() {
    }

    /**
     * Crea rol + BD si no existen (idempotente y thread-safe). Si la BD ya
     * existe no hace nada: la suite reutiliza su esquema migrado por Flyway.
     *
     * @param adminUrl      JDBC de la BD de mantenimiento (p.ej. jdbc:postgresql://localhost:5433/postgres)
     * @param adminUsuario  usuario con privilegios de CREATE ROLE / CREATE DATABASE
     * @param adminPassword password del usuario admin
     * @param base          nombre de la BD de tests (identificador SQL válido)
     * @param rol           nombre del rol propio de la BD de tests
     * @param passwordRol   password del rol de la BD de tests
     */
    public static void asegurar(String adminUrl, String adminUsuario, String adminPassword,
                                String base, String rol, String passwordRol) {
        if (base == null || base.isBlank() || !esIdentificadorSql(base)) {
            return;
        }
        if (BASES_PROTEGIDAS.contains(base)) {
            return; // jamás tocar la BD dev ni la de mantenimiento
        }
        if (rol == null || rol.isBlank() || !esIdentificadorSql(rol)) {
            throw new IllegalArgumentException(
                    "El usuario de la BD de tests debe ser un identificador SQL válido: " + rol);
        }
        String clave = adminUrl + '|' + base + '|' + rol;
        if (ASEGURADAS.contains(clave)) {
            return;
        }
        synchronized (TestDatabaseBootstrap.class) {
            if (ASEGURADAS.contains(clave)) {
                return;
            }
            try (Connection conn = DriverManager.getConnection(adminUrl, adminUsuario, adminPassword)) {
                asegurarRol(conn, rol, passwordRol == null ? "" : passwordRol);
                asegurarBaseDeDatos(conn, base, rol);
            } catch (SQLException e) {
                throw new IllegalStateException(
                        "No se pudo preparar la BD de tests '" + base + "' en " + adminUrl + ". "
                                + "¿Está PostgreSQL de desarrollo levantado? (docker compose up -d). "
                                + "Causa: " + e.getMessage(), e);
            }
            ASEGURADAS.add(clave);
        }
    }

    private static void asegurarRol(Connection conn, String rol, String passwordRol) throws SQLException {
        boolean existe;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT 1 FROM pg_roles WHERE rolname = '" + literalSql(rol) + "'")) {
            existe = rs.next();
        }
        // ALTER es también la garantía de idempotencia: el password del rol de
        // tests es siempre el del perfil test (no un secreto real).
        String sql = (existe ? "ALTER ROLE " : "CREATE ROLE ") + rol
                + " LOGIN PASSWORD '" + literalSql(passwordRol) + "'";
        ejecutar(conn, sql);
    }

    private static void asegurarBaseDeDatos(Connection conn, String base, String rol) throws SQLException {
        boolean existe;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT 1 FROM pg_database WHERE datname = '" + literalSql(base) + "'")) {
            existe = rs.next();
        }
        if (existe) {
            return;
        }
        try {
            // Autocommit: CREATE DATABASE no puede ejecutarse dentro de una transacción.
            ejecutar(conn, "CREATE DATABASE " + base + " OWNER " + rol);
        } catch (SQLException e) {
            // 42P04 duplicate_database: otra JVM de la misma suite ganó la carrera.
            if (!"42P04".equals(e.getSQLState())) {
                throw e;
            }
        }
    }

    private static void ejecutar(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    /** Identificador sin comillas: sólo minúsculas/dígitos/guion bajo (validado antes de interpolar). */
    private static boolean esIdentificadorSql(String valor) {
        return valor.matches("[a-z_][a-z0-9_]*");
    }

    /** Literal SQL simple: se duplican comillas simples (defensa ante configuración externa). */
    private static String literalSql(String valor) {
        return valor.replace("'", "''");
    }
}