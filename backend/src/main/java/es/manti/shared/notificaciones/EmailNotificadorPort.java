package es.manti.shared.notificaciones;

/**
 * Puerto de envío de email (Brevo) tras un anti-corruption layer: los
 * consumidores sólo ven esta interfaz, nunca el proveedor ni su payload
 * (skill microservicios).
 *
 * <p><b>Vive en {@code shared}</b> desde el issue #57: el envío de email es
 * infraestructura transversal (outbox de {@code profesionales} y reset de
 * contraseña de {@code usuarios}) y está en el único sentido de dependencia
 * permitido — todos los módulos hacia {@code shared}, nunca módulo a módulo
 * (ArchUnit).</p>
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
