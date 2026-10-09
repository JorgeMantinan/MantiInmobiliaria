package es.manti.profesionales.domain;

import java.util.List;

/**
 * Diagnóstico de la credencial de Telegram: lista los chats que han escrito
 * al bot (puerto en domain, adaptador con RestClient en infra).
 */
public interface TelegramDiagnosticoPort {

    /**
     * Chats vistos en las últimas actualizaciones del bot, sin duplicados.
     *
     * @throws NotificacionException si Telegram no responde
     */
    List<ChatVisto> chatsVistos();
}
