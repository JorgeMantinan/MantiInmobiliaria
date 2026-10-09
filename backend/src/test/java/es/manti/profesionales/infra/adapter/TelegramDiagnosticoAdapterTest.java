package es.manti.profesionales.infra.adapter;

import es.manti.profesionales.application.NotificacionesProperties;
import es.manti.profesionales.domain.ChatVisto;
import es.manti.profesionales.domain.NotificacionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Diagnóstico de chats ({@code getUpdates}): agrupación por chat, filtrado de
 * actualizaciones sin chat, recorte de la muestra y clasificación de errores
 * del Bot API. Cliente HTTP mockeado, cero red.
 */
class TelegramDiagnosticoAdapterTest {

    private static final String TOKEN = "123456-ABCdef";
    private static final String URL_UPDATES = "https://api.telegram.org/bot" + TOKEN + "/getUpdates";

    private MockRestServiceServer servidor;
    private TelegramDiagnosticoAdapter adapter;

    @BeforeEach
    void configurar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        adapter = new TelegramDiagnosticoAdapter(builder.build(),
                new NotificacionesProperties(true,
                        new NotificacionesProperties.Telegram(TOKEN, "-100123"), null),
                new ObjectMapper());
    }

    @Test
    void chatsVistos_sinToken_lanzaFalloDefinitivo() {
        // hermeticidad: cualquier petición inesperada revienta en el mock, sin red
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        TelegramDiagnosticoAdapter sinToken = new TelegramDiagnosticoAdapter(builder.build(),
                new NotificacionesProperties(true,
                        new NotificacionesProperties.Telegram("  ", "-100123"), null),
                new ObjectMapper());

        assertThatThrownBy(sinToken::chatsVistos)
                .isInstanceOfSatisfying(NotificacionException.class, e -> {
                    assertThat(e).hasMessage("TELEGRAM_BOT_TOKEN sin configurar");
                    assertThat(e.esReintentable()).isFalse();
                });
    }

    @Test
    void chatsVistos_ok_agrupaChatsYFiltraActualizacionesSinChat() throws Exception {
        servidor.expect(requestTo(URL_UPDATES)).andRespond(withSuccess("""
                {"ok":true,"result":[
                  {"message":{"chat":{"id":-100123,"type":"group"},"text":"hola grupo"}},
                  {"message":{"chat":{"id":-100123,"type":"group"},"text":"segundo mensaje"}},
                  {"channel_post":{"chat":{"id":42,"type":"channel"},"text":"aviso canal"}},
                  {"update_id":99}
                ]}""", MediaType.APPLICATION_JSON));

        List<ChatVisto> chats = adapter.chatsVistos();

        // Un solo chat de grupo: se conserva el PRIMER mensaje con texto
        assertThat(chats).hasSize(2);
        assertThat(chats.get(0)).isEqualTo(
                new ChatVisto("-100123", "group", "hola grupo"));
        assertThat(chats.get(1)).isEqualTo(
                new ChatVisto("42", "channel", "aviso canal"));
        servidor.verify();
    }

    @Test
    void chatsVistos_previoSinTexto_seReemplazaPorElSiguienteConTexto() throws Exception {
        servidor.expect(requestTo(URL_UPDATES)).andRespond(withSuccess("""
                {"ok":true,"result":[
                  {"message":{"chat":{"id":7,"type":"private"}}},
                  {"message":{"chat":{"id":7,"type":"private"},"text":"primer texto"}}
                ]}""", MediaType.APPLICATION_JSON));

        List<ChatVisto> chats = adapter.chatsVistos();

        assertThat(chats).containsExactly(new ChatVisto("7", "private", "primer texto"));
        servidor.verify();
    }

    @Test
    void chatsVistos_muestraLarga_laRecortaA80Caracteres() throws Exception {
        String textoLargo = "x".repeat(120);
        servidor.expect(requestTo(URL_UPDATES)).andRespond(withSuccess("""
                {"ok":true,"result":[
                  {"message":{"chat":{"id":7,"type":"private"},"text":"%s"}}
                ]}""".formatted(textoLargo), MediaType.APPLICATION_JSON));

        List<ChatVisto> chats = adapter.chatsVistos();

        assertThat(chats.get(0).textoMuestra()).hasSize(81).endsWith("\u2026");
        servidor.verify();
    }

    @Test
    void chatsVistos_conOkFalse_lanzaFalloDefinitivoConLaDescripcion() {
        servidor.expect(requestTo(URL_UPDATES)).andRespond(withSuccess(
                "{\"ok\":false,\"description\":\"Unauthorized\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(adapter::chatsVistos)
                .isInstanceOfSatisfying(NotificacionException.class, e -> {
                    assertThat(e).hasMessage("Telegram devolvió ok=false: Unauthorized");
                    assertThat(e.esReintentable()).isFalse();
                });

        servidor.verify();
    }

    @Test
    void chatsVistos_conJsonNoInterpretable_lanzaFalloReintentable() {
        servidor.expect(requestTo(URL_UPDATES)).andRespond(withSuccess(
                "esto no es json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(adapter::chatsVistos)
                .isInstanceOfSatisfying(NotificacionException.class, e -> {
                    assertThat(e).hasMessage("Respuesta de Telegram no interpretable");
                    assertThat(e.esReintentable()).isTrue();
                });

        servidor.verify();
    }

    @Test
    void chatsVistos_conRespuestaVacia_lanzaFalloReintentable() {
        servidor.expect(requestTo(URL_UPDATES)).andRespond(withSuccess());

        assertThatThrownBy(adapter::chatsVistos)
                .isInstanceOfSatisfying(NotificacionException.class, e -> {
                    assertThat(e).hasMessage("Telegram devolvió una respuesta vacía");
                    assertThat(e.esReintentable()).isTrue();
                });

        servidor.verify();
    }

    @Test
    void chatsVistos_conHttp4xx_lanzaFalloDefinitivo() {
        servidor.expect(requestTo(URL_UPDATES))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(adapter::chatsVistos)
                .isInstanceOfSatisfying(NotificacionException.class, e -> {
                    assertThat(e).hasMessage("Telegram rechazó getUpdates (HTTP 403)");
                    assertThat(e.esReintentable()).isFalse();
                });

        servidor.verify();
    }

    @Test
    void chatsVistos_conHttp5xx_lanzaFalloReintentable() {
        servidor.expect(requestTo(URL_UPDATES))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        assertThatThrownBy(adapter::chatsVistos)
                .isInstanceOfSatisfying(NotificacionException.class, e -> {
                    assertThat(e).hasMessage("Telegram no disponible (HTTP 504)");
                    assertThat(e.esReintentable()).isTrue();
                });

        servidor.verify();
    }

    @Test
    void chatsVistos_conErrorDeRed_lanzaFalloReintentable() {
        servidor.expect(requestTo(URL_UPDATES))
                .andRespond(request -> { throw new IOException("sin conexión"); });

        assertThatThrownBy(adapter::chatsVistos)
                .isInstanceOfSatisfying(NotificacionException.class, e -> {
                    assertThat(e).hasMessage("Error de red con Telegram (ResourceAccessException)");
                    assertThat(e.esReintentable()).isTrue();
                });

        servidor.verify();
    }
}