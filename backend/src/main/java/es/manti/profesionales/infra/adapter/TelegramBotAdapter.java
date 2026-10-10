package es.manti.profesionales.infra.adapter;

import es.manti.shared.notificaciones.NotificacionesProperties;
import es.manti.shared.notificaciones.NotificacionException;
import es.manti.profesionales.domain.TelegramNotificadorPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Adaptador del <b>Bot API de Telegram</b> para el envío de avisos.
 *
 * <p>El token viaja en la URI ({@code /bot<token>/sendMessage}): por eso los
 * mensajes de error se construyen aquí SIN replicar la URI ni el cuerpo de la
 * respuesta, y el despachador además retira el token de cualquier texto antes
 * de guardarlo en {@code ultimo_error} o de loguearlo.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotAdapter implements TelegramNotificadorPort {

    static final String URL_ENVIO = "https://api.telegram.org/bot{token}/sendMessage";

    private final RestClient notificacionesRestClient;
    private final NotificacionesProperties propiedades;
    private final ObjectMapper objectMapper;

    @Override
    public void enviar(String chatId, String texto) {
        String token = token();
        if (chatId == null || chatId.isBlank()) {
            throw NotificacionException.definitiva("TELEGRAM_CHAT_ID sin configurar");
        }
        String cuerpo = objectMapper.writeValueAsString(Map.of("chat_id", chatId, "text", texto));
        try {
            notificacionesRestClient.post()
                    .uri(URL_ENVIO, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            int estado = e.getStatusCode().value();
            throw estado >= 400 && estado < 500
                    // chat_id inexistente o token inválido: reintentar no lo arregla
                    ? NotificacionException.definitiva("Telegram rechazó la petición (HTTP " + estado + ")")
                    : NotificacionException.reintentable("Telegram no disponible (HTTP " + estado + ")");
        } catch (RestClientException e) {
            throw NotificacionException.reintentable(
                    "Error de red con Telegram (" + e.getClass().getSimpleName() + ")");
        }
        log.debug("Aviso de Telegram enviado al chat {}", chatId);
    }

    /** @throws NotificacionException definitiva si falta el token */
    private String token() {
        NotificacionesProperties.Telegram telegram = propiedades.telegram();
        if (telegram == null || !telegram.tieneToken()) {
            throw NotificacionException.definitiva("TELEGRAM_BOT_TOKEN sin configurar");
        }
        return telegram.botToken();
    }
}
