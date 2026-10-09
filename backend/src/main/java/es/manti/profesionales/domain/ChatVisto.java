package es.manti.profesionales.domain;

/**
 * Chat observado por el diagnóstico de Telegram ({@code getUpdates}):
 * el chat_id que está escribiendo el bot, su tipo y un trozo del último
 * mensaje. Se usa para descubrir el {@code chat_id} de un grupo.
 */
public record ChatVisto(String chatId, String tipo, String textoMuestra) {
}
