package es.manti.usuarios.api;

import es.manti.shared.notificaciones.EmailNotificadorPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Camino ASÍNCRONO real del envío del enlace (issue #57, revisión del PR #64):
 * con {@code auth.password-reset.sync=false} la publicación del evento
 * {@code EnlacePasswordReset} se consume en el executor {@code passwordResetEmail}
 * DESPUÉS del commit.
 *
 * <p>La prueba demuestra las dos propiedades del diseño bloqueando al proveedor
 * con un latch: la petición termina con el 202 mientras el envío sigue dentro
 * de la llamada — el tiempo de respuesta no depende de Brevo.</p>
 */
@SpringBootTest(properties = {
        "auth.password-reset.sync=false",
        "manti.notificaciones.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetAsyncEnvioTest {

    private static final String FORGOT = "/api/v1/auth/password/forgot";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EmailNotificadorPort correo;

    /** Liberador del latch del proveedor; en el finally para no dejar hilos colgados. */
    private CountDownLatch proveedorLiberado = new CountDownLatch(1);

    @AfterEach
    void limpiar() {
        proveedorLiberado.countDown();
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    @Test
    void forgot_conEnvioAsincrono_devuelve202SinEsperarAlProveedor() throws Exception {
        CountDownLatch proveedorInvocado = new CountDownLatch(1);
        CountDownLatch envioTerminado = new CountDownLatch(1);
        proveedorLiberado = new CountDownLatch(1);
        doAnswer(invocation -> {
            proveedorInvocado.countDown();
            proveedorLiberado.await(5, TimeUnit.SECONDS);
            envioTerminado.countDown();
            return null;
        }).when(correo).enviar(anyString(), anyString(), anyString());

        String email = "it-" + UUID.randomUUID() + "@example.com";
        registrar(email);

        // 1) La petición termina con el proveedor AÚN BLOQUEADO: si el envío
        //    fuera síncrono, perform() se quedaría esperando al latch.
        mockMvc.perform(post(FORGOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());

        // 2) El listener AFTER_COMMIT invoca al proveedor en el executor…
        assertThat(proveedorInvocado.await(5, TimeUnit.SECONDS))
                .as("el envío debe ocurrir en el executor tras el commit")
                .isTrue();
        // 3) …y la respuesta ya había salido mientras el proveedor seguía dentro.
        assertThat(proveedorLiberado.getCount())
                .as("el 202 salió con la llamada al proveedor todavía en curso")
                .isEqualTo(1);

        proveedorLiberado.countDown();
        assertThat(envioTerminado.await(5, TimeUnit.SECONDS)).isTrue();

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq(email), anyString(), html.capture());
        assertThat(html.getValue()).contains("/restablecer?token=");
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
