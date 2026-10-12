package es.manti.usuarios.api;

import es.manti.shared.notificaciones.EmailNotificadorPort;
import es.manti.usuarios.domain.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Carrera de la relectura en {@code solicitar} (Bajo de la revisión del
 * PR #64): la cuenta se borra entre la lectura inicial y la relectura en
 * transacción. Sin el catch sería un 404 (oráculo de status que revela la
 * carrera); con él, se aborta sin enviar y el 202 sigue siendo el mismo.
 *
 * <p>Se fuerza la carrera con un spy de {@link UsuarioRepository}: el registro
 * y la lectura por email funcionan normal, pero {@code findById} devuelve
 * vacío — exactamente el estado tras un DELETE concurrente.</p>
 */
@SpringBootTest(properties = "manti.notificaciones.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetCarreraTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private UsuarioRepository usuarios;

    @MockitoBean
    private EmailNotificadorPort correo;

    @AfterEach
    void limpiarUsuariosCreados() {
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    @Test
    void forgot_conCuentaBorradaEnCarrera_devuelve202SinEnviar() throws Exception {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        // Registro normal (el spy aún no está manipulado).
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana Test","email":"%s","password":"secreto123"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        // A partir de aquí la relectura por id "no encuentra" la cuenta.
        doReturn(Optional.empty()).when(usuarios).findById(any());

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());

        verifyNoInteractions(correo);
        assertThatSinTokens(email);
    }

    private void assertThatSinTokens(String email) {
        Integer tokens = jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_tokens t "
                        + "JOIN usuarios u ON u.id = t.usuario_id WHERE u.email = ?",
                Integer.class, email);
        org.assertj.core.api.Assertions.assertThat(tokens)
                .as("la carrera aborta sin crear token")
                .isZero();
    }
}
