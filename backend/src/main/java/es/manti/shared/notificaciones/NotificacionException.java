package es.manti.shared.notificaciones;

/**
 * Fallo al enviar una notificación por un canal externo (Brevo/Telegram).
 *
 * <p>El despachador distingue dos clases de error:</p>
 * <ul>
 *   <li><b>reintentables</b> (caída de red, 5xx, timeout): la fila vuelve a
 *       {@code PENDIENTE} hasta 3 intentos y después cae a {@code FALLIDA}.</li>
 *   <li><b>definitivas</b> (credencial ausente o rechazada por el proveedor):
 *       reintentar no arregla nada, así que la fila pasa a {@code FALLIDA} en
 *       el primer intento y se guarda el error para diagnóstico.</li>
 * </ul>
 */
public class NotificacionException extends RuntimeException {

    private final boolean reintentable;

    private NotificacionException(String mensaje, boolean reintentable) {
        super(mensaje);
        this.reintentable = reintentable;
    }

    public static NotificacionException reintentable(String mensaje) {
        return new NotificacionException(mensaje, true);
    }

    public static NotificacionException definitiva(String mensaje) {
        return new NotificacionException(mensaje, false);
    }

    public boolean esReintentable() {
        return reintentable;
    }
}
