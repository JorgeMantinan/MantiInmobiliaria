package es.manti.profesionales.domain;

/**
 * Estado del ciclo de vida de una fila del outbox:
 * {@link #PENDIENTE} → {@link #ENVIADA} o {@link #FALLIDA}.
 *
 * <p>{@code intentos} cuenta los intentos de envío: con 3 fallos
 * reintentables la fila pasa a {@link #FALLIDA}; un error NO reintentable
 * (credencial ausente) la manda directamente a {@link #FALLIDA}.</p>
 */
public enum EstadoNotificacion {
    PENDIENTE,
    ENVIADA,
    FALLIDA
}
