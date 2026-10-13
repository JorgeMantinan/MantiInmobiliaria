package es.manti.profesionales.domain;

/**
 * A quién va dirigida la fila del outbox: un {@link #PROFESIONAL} concreto
 * (email personal), el {@link #GRUPO} de Telegram configurado (chat_id) o el
 * {@link #USUARIO} dueño de una alerta de búsqueda (issue #96, sólo canal
 * EMAIL: su aviso llega a la dirección de su cuenta). La coherencia con
 * {@code destinatario_id} la garantiza
 * {@code ck_notificaciones_outbox_destinatario} en {@code V4__profesionales.sql}
 * (ampliada en {@code V13__alertas_busqueda.sql}).
 */
public enum DestinatarioNotificacion {
    PROFESIONAL,
    GRUPO,
    USUARIO
}
