package es.manti.profesionales;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import es.manti.profesionales.application.GestionProfesionalService;
import es.manti.profesionales.domain.Profesional;
import es.manti.profesionales.domain.ProfesionalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #51 — concurrencia en el PATCH de moderación (control optimista
 * {@code @Version} + log con actor).
 *
 * <p>La carrera se simula de forma DETERMINISTA con el mismo patrón que
 * {@code RegistroConflictivoTest}: un spy del repositorio que, DESPUÉS de que
 * el servicio lee al profesional, deja constancia en BD de que OTRO
 * administrador ya ha moderado (estado + versión). El flush del primer admin
 * entonces no afecta a ninguna fila y debe salir como 409 con el ProblemDetail
 * fijo del contrato. Sin el fix ({@code @Version} + advice) este test falla:
 * el PATCH devolvería 200 y revierte en silencio al otro admin.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminProfesionalConcurrenciaTest {

    private static final String PASSWORD = "secreto123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** Conexión CRUDA para la "otra" transacción: JdbcTemplate enlazaría la
     * conexión de la tx del propio request (DataSourceUtils, mismo hilo) y el
     * UPDATE concurrente se revolvaría con ella. */
    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private ProfesionalRepository profesionales;

    private String tokenAdmin;
    private long idAdmin;

    @BeforeEach
    void crearAdmin() throws Exception {
        String email = "it-admin-conc-" + UUID.randomUUID() + "@example.com";
        jdbc.update("""
                INSERT INTO usuarios (email, password_hash, nombre, rol, activo)
                VALUES (?, ?, 'Admin Test', 'ADMIN', true)
                """, email, passwordEncoder.encode(PASSWORD));
        idAdmin = jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", Long.class, email);
        tokenAdmin = login(email);
    }

    @AfterEach
    void limpiarFixtures() {
        FixturesProfesionales.limpiar(jdbc);
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    // ------------------------------------------------------------------
    // 409 — carrera real entre dos administradores
    // ------------------------------------------------------------------

    @Test
    void cambiarEstado_conOtroAdminModerandoEntreLecturaYEscritura_devuelve409YNoRevirtio() throws Exception {
        long id = FixturesProfesionales.profesional(
                jdbc, "it-pro-conflicto-" + UUID.randomUUID() + "@example.com", "PENDIENTE", "fontanero");

        // El "otro" admin gana la carrera: commitea RECHAZADO y sube la versión
        // DESPUÉS de que este PATCH ya ha leído la fila (version = 0).
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Optional<Profesional> leido = (Optional<Profesional>) invocation.callRealMethod();
            Long profesionalId = invocation.getArgument(0);
            moderarComoOtroAdmin(profesionalId, "RECHAZADO");
            return leido;
        }).when(profesionales).porId(id);

        Logger logger = (Logger) LoggerFactory.getLogger(GestionProfesionalService.class);
        ListAppender<ILoggingEvent> registro = new ListAppender<>();
        registro.start();
        logger.addAppender(registro);
        try {
            mockMvc.perform(patch("/api/v1/admin/profesionales/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estado\": \"ACTIVO\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type")
                            .value("https://api.manti.dev/problems/conflicto-concurrencia"))
                    .andExpect(jsonPath("$.title").value("Conflicto de concurrencia"))
                    .andExpect(jsonPath("$.detail")
                            .value("El profesional fue modificado por otro administrador; recarga la lista."))
                    .andExpect(jsonPath("$.status").value(409));

            // El ACTIVO perdedor no se cuela en BD: gana el RECHAZADO del otro admin
            String estado = jdbc.queryForObject(
                    "SELECT estado FROM profesionales WHERE id = ?", String.class, id);
            assertThat(estado).isEqualTo("RECHAZADO");

            // La moderación perdedora NUNCA se registra como éxito
            assertThat(registro.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .noneSatisfy(mensaje -> assertThat(mensaje).contains("→ ACTIVO por"));
        } finally {
            logger.detachAppender(registro);
        }
    }
    // ------------------------------------------------------------------
    // Versionado: sin carreras no hay 409 (evita falsos positivos)
    // ------------------------------------------------------------------

    @Test
    void cambiarEstado_exitoso_incrementaLaVersionDeLaFila() throws Exception {
        long id = FixturesProfesionales.profesional(
                jdbc, "it-pro-version-" + UUID.randomUUID() + "@example.com", "PENDIENTE", "fontanero");
        assertThat(versionDe(id)).isZero();

        mockMvc.perform(patch("/api/v1/admin/profesionales/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"ACTIVO\"}"))
                .andExpect(status().isOk());

        assertThat(versionDe(id)).isEqualTo(1);
    }

    @Test
    void cambiarEstado_secuencialSinOtraEscritura_noDevuelve409() throws Exception {
        long id = FixturesProfesionales.profesional(
                jdbc, "it-pro-secuencial-" + UUID.randomUUID() + "@example.com", "PENDIENTE", "fontanero");

        // Dos moderaciones seguidas SIN carreras: el cliente no tiene que
        // recargar nada entre medias (el contrato no exige If-Match).
        mockMvc.perform(patch("/api/v1/admin/profesionales/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"ACTIVO\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/admin/profesionales/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"RECHAZADO\"}"))
                .andExpect(status().isOk());

        assertThat(estadoDe(id)).isEqualTo("RECHAZADO");
        assertThat(versionDe(id)).isEqualTo(2);
    }

    @Test
    void cambiarEstado_conOtroAdminAplicandoElMismoEstado_devuelve409() throws Exception {
        long id = FixturesProfesionales.profesional(
                jdbc, "it-pro-mismo-estado-" + UUID.randomUUID() + "@example.com", "PENDIENTE", "fontanero");

        // El otro admin ya dejó ACTIVO (versión +1); escribir ACTIVO de nuevo
        // es una escritura CONCURRENTE y también debe saltar, no re-validar.
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Optional<Profesional> leido = (Optional<Profesional>) invocation.callRealMethod();
            Long profesionalId = invocation.getArgument(0);
            moderarComoOtroAdmin(profesionalId, "ACTIVO");
            return leido;
        }).when(profesionales).porId(id);

        mockMvc.perform(patch("/api/v1/admin/profesionales/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"ACTIVO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("https://api.manti.dev/problems/conflicto-concurrencia"));
    }

    // ------------------------------------------------------------------
    // Documentación (springdoc): el 409 es parte del contrato
    // ------------------------------------------------------------------

    @Test
    void apiDocs_patchProfesional_documentaEl409DeConcurrencia() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/profesionales/{id}'].patch.responses['409']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/profesionales/{id}'].patch.responses['409'].description")
                        .value(containsString("Conflicto de concurrencia")));
    }

    // ------------------------------------------------------------------
    // Log de éxito con actor
    // ------------------------------------------------------------------

    @Test
    void cambiarEstado_comoAdmin_registraElActorDelBearerEnElLog() throws Exception {
        long id = FixturesProfesionales.profesional(
                jdbc, "it-pro-actor-" + UUID.randomUUID() + "@example.com", "PENDIENTE", "fontanero");

        Logger logger = (Logger) LoggerFactory.getLogger(GestionProfesionalService.class);
        ListAppender<ILoggingEvent> registro = new ListAppender<>();
        registro.start();
        logger.addAppender(registro);
        try {
            mockMvc.perform(patch("/api/v1/admin/profesionales/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estado\": \"ACTIVO\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.estado").value("ACTIVO"));

            // El actor es el `sub` del JWT (= Authentication.getName())
            assertThat(registro.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .contains("Profesional " + id + " → ACTIVO por " + idAdmin);
        } finally {
            logger.detachAppender(registro);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * El "otro" administrador modera en paralelo: su UPDATE commitea en una
     * conexión propia (autocommit, FUERA de la tx del request) y sube la
     * versión de la fila que el primer admin acaba de leer.
     */
    private void moderarComoOtroAdmin(long profesionalId, String estado) throws SQLException {
        try (Connection conexion = dataSource.getConnection();
             PreparedStatement sentencia = conexion.prepareStatement(
                     "UPDATE profesionales SET estado = ?, version = version + 1 WHERE id = ?")) {
            sentencia.setString(1, estado);
            sentencia.setLong(2, profesionalId);
            sentencia.executeUpdate();
        }
    }

    private long versionDe(long id) {
        return jdbc.queryForObject("SELECT version FROM profesionales WHERE id = ?", Long.class, id);
    }

    private String estadoDe(long id) {
        return jdbc.queryForObject("SELECT estado FROM profesionales WHERE id = ?", String.class, id);
    }

    private String login(String email) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.accessToken");
    }
}
