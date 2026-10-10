package es.manti.profesionales.infra.adapter;

import es.manti.shared.notificaciones.NotificacionesProperties;
import es.manti.profesionales.domain.ChatVisto;
import es.manti.shared.notificaciones.NotificacionException;
import es.manti.profesionales.domain.TelegramDiagnosticoPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador del <b>Bot API de Telegram</b> para el diagnóstico de chats
 * ({@code getUpdates}).
 *
 * <p>Se lee la respuesta como árbol JSON en lugar de mapearla a records: la
 * API devuelve decenas de campos que no nos interesan y un campo nuevo de
 * Telegram no debe tumbar el diagnóstico. Sólo se extraen {@code chat.id},
 * {@code chat.type} y un extracto del último mensaje.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramDiagnosticoAdapter implements TelegramDiagnosticoPort {

    static final String URL_ACTUALIZACIONES = "https://api.telegram.org/bot{token}/getUpdates";

    /** Longitud del extracto de mensaje que se devuelve al administrador. */
    static final int LARGO_MUESTRA = 80;

    private final RestClient notificacionesRestClient;
    private final NotificacionesProperties propiedades;
    private final ObjectMapper objectMapper;

    @Override
    public List<ChatVisto> chatsVistos() {
        NotificacionesProperties.Telegram telegram = propiedades.telegram();
        if (telegram == null || !telegram.tieneToken()) {
            throw NotificacionException.definitiva("TELEGRAM_BOT_TOKEN sin configurar");
        }
        String json = pedirActualizaciones(telegram.botToken());
        return resumirChats(json);
    }

    private String pedirActualizaciones(String token) {
        try {
            String cuerpo = notificacionesRestClient.get()
                    .uri(URL_ACTUALIZACIONES, token)
                    .retrieve()
                    .body(String.class);
            if (cuerpo == null) {
                throw NotificacionException.reintentable("Telegram devolvió una respuesta vacía");
            }
            return cuerpo;
        } catch (RestClientResponseException e) {
            int estado = e.getStatusCode().value();
            throw estado >= 400 && estado < 500
                    ? NotificacionException.definitiva("Telegram rechazó getUpdates (HTTP " + estado + ")")
                    : NotificacionException.reintentable("Telegram no disponible (HTTP " + estado + ")");
        } catch (RestClientException e) {
            throw NotificacionException.reintentable(
                    "Error de red con Telegram (" + e.getClass().getSimpleName() + ")");
        }
    }

    /** Una fila por chat, conservando el primer mensaje que tenga texto. */
    private List<ChatVisto> resumirChats(String json) {
        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(json);
        } catch (RuntimeException jsonInvalido) {
            throw NotificacionException.reintentable("Respuesta de Telegram no interpretable");
        }
        if (!raiz.path("ok").asBoolean(false)) {
            throw NotificacionException.definitiva("Telegram devolvió ok=false: "
                    + raiz.path("description").asText("sin descripción"));
        }
        Map<String, ChatVisto> porChat = new LinkedHashMap<>();
        for (JsonNode actualizacion : raiz.path("result")) {
            JsonNode mensaje = actualizacion.has("message")
                    ? actualizacion.get("message")
                    : actualizacion.path("channel_post");
            JsonNode chat = mensaje.path("chat");
            String chatId = chat.path("id").asText();
            if (chatId.isBlank()) {
                continue;
            }
            String tipo = chat.path("type").asText("desconocido");
            String muestra = recortar(mensaje.path("text").asText(""));
            ChatVisto previo = porChat.get(chatId);
            if (previo == null || (previo.textoMuestra().isBlank() && !muestra.isBlank())) {
                porChat.put(chatId, new ChatVisto(chatId, tipo, muestra));
            }
        }
        log.debug("Diagnóstico de Telegram: {} chat(s) vistos", porChat.size());
        return List.copyOf(porChat.values());
    }

    private static String recortar(String texto) {
        if (texto.length() <= LARGO_MUESTRA) {
            return texto;
        }
        return texto.substring(0, LARGO_MUESTRA) + "…";
    }
}
