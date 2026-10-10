package es.manti.usuarios.api;

import es.manti.shared.notificaciones.EmailNotificadorPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Interruptor global {@code manti.notificaciones.enabled=false} (Bajo de la
 * revisión del PR #64): el token de restablecimiento SÍ se crea, pero el
 * sender no lo envía y sólo deja un warn. El 202 del endpoint es idéntico.
 *
 * <p>El perfil test ya trae {@code enabled=false}; sólo hay que forzar
 * {@code sync=true} (también el default del perfil test) para que el envío
 * pase por la puerta única del sender y el guard se ejerza sobre el mock.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetNotificacionesApagadasTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EmailNotificadorPort correo;

    @AfterEach
    void limpiarUsuariosCreados() {
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    @Test
    void forgot_conNotificacionesApagadas_creaTokenPeroNoEnvia() throws Exception {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        registrar(email);

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());

        // El token se creó (la cuenta podría volver a pedir el enlace)…
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_tokens t "
                        + "JOIN usuarios u ON u.id = t.usuario_id WHERE u.email = ?",
                Integer.class, email))
                .as("con notificaciones apagadas el token se crea igual")
                .isEqualTo(1);
        // …pero nada salió hacia el proveedor.
        verifyNoInteractions(correo);
    }

    private void registrar(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana Test","email":"%s","password":"secreto123"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }
}
