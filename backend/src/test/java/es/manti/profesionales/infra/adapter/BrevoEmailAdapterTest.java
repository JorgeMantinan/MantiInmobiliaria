package es.manti.profesionales.infra.adapter;

import es.manti.profesionales.application.NotificacionesProperties;
import es.manti.profesionales.domain.NotificacionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Anti-corruption layer de Brevo: credencial ausente, clasificación de errores
 * HTTP (4xx definitivo vs 5xx/red reintentable) y forma del payload — TODO con
 * el cliente HTTP mockeado (cero red en la suite).
 */
class BrevoEmailAdapterTest {

    private static final String DESTINO = "cliente@example.com";
    private static final String ASUNTO = "Nueva solicitud en Talavera";
    private static final String HTML = "<p>Se ha roto una tubería</p>";

    private MockRestServiceServer servidor;
    private BrevoEmailAdapter adapter;

    @BeforeEach
    void configurar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        adapter = new BrevoEmailAdapter(builder.build(),
                new NotificacionesProperties(true, null,
                        new NotificacionesProperties.Brevo(
                                "clave-secreta-brevo", "no-reply@manti.dev", "Manti")),
                new ObjectMapper());
    }

    @Test
    void enviar_sinApiKey_lanzaFalloDefinitivo() {
        // hermeticidad: cualquier petición inesperada revienta en el mock, sin red
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        BrevoEmailAdapter sinCredencial = new BrevoEmailAdapter(builder.build(),
                new NotificacionesProperties(true, null, null), new ObjectMapper());

        assertThatThrownBy(() -> sinCredencial.enviar(DESTINO, ASUNTO, HTML))
                .isInstanceOfSatisfying(NotificacionException.class, e -> {
                    assertThat(e).hasMessage("BREVO_API_KEY sin configurar");
                    assertThat(e.esReintentable()).isFalse();
                });
    }

    @Test
    void enviar_apiKeyEnBlanco_lanzaFalloDefinitivo() {
        // hermeticidad: cualquier petición inesperada revienta en el mock, sin red
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        BrevoEmailAdapter sinCredencial = new BrevoEmailAdapter(builder.build(),
                new NotificacionesProperties(true, null,
                        new NotificacionesProperties.Brevo("  ", "no-reply@manti.dev", "Manti")),
                new ObjectMapper());

        assertThatThrownBy(() -> sinCredencial.enviar(DESTINO, ASUNTO, HTML))
                .isInstanceOfSatisfying(NotificacionException.class, e ->
                        assertThat(e).hasMessage("BREVO_API_KEY sin configurar"));
    }

    @Test
    void enviar_ok_enviaElPayloadJsonConLaCabeceraApiKey() throws Exception {
        servidor.expect(requestTo(BrevoEmailAdapter.URL_ENVIO))
                .andExpect(header("api-key", "clave-secreta-brevo"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"subject\":\"" + ASUNTO + "\"")))
                .andExpect(content().string(containsString("\"email\":\"" + DESTINO + "\"")))
                .andExpect(content().string(containsString("\"htmlContent\":\"" + HTML + "\"")))
                .andRespond(withSuccess());

        adapter.enviar(DESTINO, ASUNTO, HTML);

        servidor.verify();
    }

    @Test
    void enviar_conHttp4xx_lanzaFalloDefinitivo() {
        servidor.expect(requestTo(BrevoEmailAdapter.URL_ENVIO))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> adapter.enviar(DESTINO, ASUNTO, HTML))
                .isInstanceOfSatisfying(NotificacionException.class, e -> {
                    assertThat(e).hasMessage("Brevo rechazó la petición (HTTP 400)");
                    assertThat(e.esReintentable()).isFalse();
                });

        servidor.verify();
    }

    @Test
    void enviar_conHttp5xx_lanzaFalloReintentable() {
        servidor.expect(requestTo(BrevoEmailAdapter.URL_ENVIO))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> adapter.enviar(DESTINO, ASUNTO, HTML))
                .isInstanceOfSatisfying(NotificacionException.class, e -> {
                    assertThat(e).hasMessage("Brevo no disponible (HTTP 503)");
                    assertThat(e.esReintentable()).isTrue();
                });

        servidor.verify();
    }

    @Test
    void enviar_conErrorDeRed_lanzaFalloReintentable() {
        servidor.expect(requestTo(BrevoEmailAdapter.URL_ENVIO))
                .andRespond(request -> { throw new IOException("conexion cortada"); });

        assertThatThrownBy(() -> adapter.enviar(DESTINO, ASUNTO, HTML))
                .isInstanceOfSatisfying(NotificacionException.class, e -> {
                    assertThat(e).hasMessage("Error de red con Brevo (ResourceAccessException)");
                    assertThat(e.esReintentable()).isTrue();
                });

        servidor.verify();
    }
}