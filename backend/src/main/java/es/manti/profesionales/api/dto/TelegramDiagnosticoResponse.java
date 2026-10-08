package es.manti.profesionales.api.dto;

import java.util.List;

/**
 * Respuesta del diagnóstico de Telegram: los chats que han escrito al bot,
 * con el {@code chat_id} que hace falta copiar en {@code TELEGRAM_CHAT_ID}.
 *
 * @param chatsVistos sin duplicados, en el orden en que aparecen
 */
public record TelegramDiagnosticoResponse(List<ChatVisto> chatsVistos) {

    /** Chat observado: identificador, tipo (private/group/supergroup/channel) y extracto. */
    public record ChatVisto(String chatId, String tipo, String textoMuestra) {
    }
}
