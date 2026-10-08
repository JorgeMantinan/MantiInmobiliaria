package es.manti.profesionales.domain;

/**
 * Adaptador de email (Brevo) tras un puerto: el dominio y la aplicación sólo
 * ven esta interfaz, nunca el proveedor ni su payload (anti-corruption layer,
 * skill microservicios).
 */
public interface EmailNotificadorPort {

    /**
     * Envía un email HTML.
     *
     * @param destino email del destinatario
     * @param asunto  asunto del mensaje
     * @param html    cuerpo HTML ya escapado
     * @throws NotificacionException si el proveedor falla o falta la credencial
     */
    void enviar(String destino, String asunto, String html);
}
