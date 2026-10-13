package es.manti.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verificacion de control del issue #28: la suite debe correr SIEMPRE contra la
 * BD dedicada manti_test (rol propio incluido) y jamas contra la BD dev
 * manti (manti_dev). Si alguien rompe el datasource del perfil test,
 * este test falla en el primer assert.
 */
@SpringBootTest
@ActiveProfiles("test")
class TestDatabaseIsolationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void datasource_conectaAMantiTest_yNuncaALaBdDev() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn.getCatalog()).isEqualTo("manti_test");
        }

        String baseActual = jdbc.queryForObject("SELECT current_database()", String.class);
        String usuarioActual = jdbc.queryForObject("SELECT current_user", String.class);

        assertThat(baseActual).isEqualTo("manti_test");
        assertThat(baseActual).isNotEqualTo("manti");
        assertThat(usuarioActual).isEqualTo("manti_test");

        if (dataSource instanceof HikariDataSource hikari) {
            assertThat(hikari.getJdbcUrl())
                    .contains("/manti_test")
                    .doesNotContain("/manti_dev");
        }
    }

    @Test
    void flyway_migroElEsquemaDeTestDesdeCero() {
        // V1..V11 en el momento de escribir este test; el limite inferior evita
        // falsos positivos si en el futuro se borra una migracion.
        Integer migraciones = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(migraciones).isGreaterThanOrEqualTo(11);
    }
}