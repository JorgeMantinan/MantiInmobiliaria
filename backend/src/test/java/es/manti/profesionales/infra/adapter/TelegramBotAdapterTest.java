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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Adaptador de avisos por Telegram: token obligatorio, chat_id obligatorio y
 * clasificación de errores del Bot API (4xx = chat/token inválido → definitivo,
 * 5xx/red → reintentable). Cliente HTTP mockeado, cero red.
 */
class TelegramBotAdapterTest {

    private static final String TOKEN = "123456-ABCdef";
    private static final String URL_OK = "https://api.telegram.org/bot" + TOKEN + "/sendMessage";

    private MockRestServiceServer servidor;
    private TelegramBotAdapter adapter;

    @BeforeEach
    void configurar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        adapter = new TelegramBotAdapter(builder.build(), propiedades(TOKEN, "-100123"),
                new ObjectMapper());
    }

    @Test
    void enviar_sinToken_lanzaFalloDefinitivo() {
        // hermeticidad: cualquier petición inesperada revienta en el mock, sin red
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        TelegramBotAdapter sinToken = new TelegramBotAdapter(builder.build(),
                propiedades(null, "-100123"), new ObjectMapper());

        assertThatThrownBy(() -> sinToken.enviar("-100123", "aviso"))
                .isInstanceOfSatisfying(NotificacionException.class, e -> {
                    assertThat(e).hasMessage("TELEGRAM_BOT_TOKEN sin configurar");
                    assertThat(e.esReintentable()).isFalse();
                });
    }

    @Test
    void enviar_chatIdNulo_lanzaFalloDefinitivo() {
        assertThatThrownBy(() -> adapter.enviar(null, "aviso"))
                .isInstanceOfSatisfying(NotificacionException.class, e ->
                        assertThat(e).hasMessage("TELEGRAM_CHAT_ID sin configurar"));
    }

    @Test
    void enviar_chatIdEnBlanco_lanzaFalloDefinitivo() {
        assertThatThrownBy(() -> adapter.enviar("   ", "aviso"))
                .isInstanceOfSatisfying(NotificacionException.class, e ->
                        assertThat(e).hasMessage("TELEGRAM_CHAT_ID sin configurar"));
    }

    @Test
    void enviar_ok_posteaElJsonDelAvisoAlChat() throws Exception {
        servidor.expect(requestTo(URL_OK))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"chat_id\":\"-100123\"")))
                .andExpect(content().string(containsString("\"text\":\"aviso urgente\"")))
                .andRespond(withSuccess());

        adapter.enviar("-100123", "aviso urgente");

        servidor.verify();
    }

    @Test
    void enviar_conHttp4xx_lanzaFalloDefinitivo() {
        servidor.expect(requestTo(URL_OK))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.enviar("-100123", "aviso"))
                .isInstanceOfSatisfying(NotificacionException.class, e -> {
                    assertThat(e).hasMessage("Telegram rechazó la petición (HTTP 404)");
                    assertThat(e.esReintentable()).isFalse();
                });

        servidor.verify();
    }

    @Test
    void enviar_conHttp5xx_lanzaFalloReintentable() {
        servidor.expect(requestTo(URL_OK))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> adapter.enviar("-100123", "aviso"))
                .isInstanceOfSatisfying(NotificacionException.class, e -> {
                    assertThat(e).hasMessage("Telegram no disponible (HTTP 502)");
                    assertThat(e.esReintentable()).isTrue();
                });

        servidor.verify();
    }

    @Test
    void enviar_conErrorDeRed_lanzaFalloReintentable() {
        servidor.expect(requestTo(URL_OK))
                .andRespond(request -> { throw new IOException("sin salida a internet"); });

        assertThatThrownBy(() -> adapter.enviar("-100123", "aviso"))
                .isInstanceOfSatisfying(NotificacionException.class, e -> {
                    assertThat(e).hasMessage("Error de red con Telegram (ResourceAccessException)");
                    assertThat(e.esReintentable()).isTrue();
                });

        servidor.verify();
    }

    private static NotificacionesProperties propiedades(String token, String chatId) {
        return new NotificacionesProperties(true,
                new NotificacionesProperties.Telegram(token, chatId), null);
    }
}