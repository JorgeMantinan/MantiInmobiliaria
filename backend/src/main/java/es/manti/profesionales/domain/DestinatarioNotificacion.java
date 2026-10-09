package es.manti.profesionales.domain;

/**
 * A quién va dirigida la fila del outbox: un {@link #PROFESIONAL} concreto
 * (email personal) o el {@link #GRUPO} de Telegram configurado (chat_id).
 * La coherencia con {@code destinatario_id} la garantiza
 * {@code ck_notificaciones_outbox_destinatario} en {@code V4__profesionales.sql}.
 */
public enum DestinatarioNotificacion {
    PROFESIONAL,
    GRUPO
}
