package es.manti.profesionales.domain;

/**
 * Adaptador de Telegram (Bot API) tras un puerto, igual que
 * {@link EmailNotificadorPort}.
 */
public interface TelegramNotificadorPort {

    /**
     * Envía un mensaje de texto a un chat (usuario o grupo).
     *
     * @param chatId identificador del chat de destino
     * @param texto  mensaje sin formato
     * @throws NotificacionException si el proveedor falla o falta la credencial
     */
    void enviar(String chatId, String texto);
}
