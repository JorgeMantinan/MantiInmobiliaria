package es.manti.inmuebles.domain;

/**
 * Fila a encolar en el outbox de notificaciones compartido
 * ({@code notificaciones_outbox}, esquema del módulo profesionales): el job la
 * escribe en la MISMA transacción que avanza la marca de agua de la alerta y
 * el despachador existente (Brevo + Telegram) la envía después, fuera de ella.
 *
 * @param alertaId alerta de origen (FK de la fila; distinto de solicitud_id)
 * @param canal    EMAIL (al dueño) o TELEGRAM (al grupo configurado)
 * @param destino  email del usuario o chat_id del grupo
 * @param asunto   asunto del email (en Telegram se ignora: no lo tiene)
 * @param cuerpo   HTML para email, texto plano para Telegram (ya escapado)
 */
public record FilaAvisoAlerta(
        Long alertaId,
        CanalAvisoAlerta canal,
        String destino,
        String asunto,
        String cuerpo) {
}
