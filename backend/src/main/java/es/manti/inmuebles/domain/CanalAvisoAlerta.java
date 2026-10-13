package es.manti.inmuebles.domain;

/**
 * Canal del aviso de una alerta. Los valores coinciden con los del outbox
 * compartido ({@code canal} en {@code notificaciones_outbox}), pero es un tipo
 * PROPIO del módulo: las clases de {@code profesionales} no se importan nunca
 * (regla ArchUnit de módulos aislados).
 */
public enum CanalAvisoAlerta {
    EMAIL,
    TELEGRAM
}
