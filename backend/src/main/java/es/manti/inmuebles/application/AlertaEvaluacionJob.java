package es.manti.inmuebles.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Evaluación programada de las alertas de búsqueda (issue #96).
 *
 * <p><b>Cada 15 minutos</b> ({@code manti.alertas.cadencia-ms}, fuera de la
 * hora punta no aplica: es un fixedDelay desde el arranque) y SÓLO si
 * {@code manti.alertas.enabled=true} (default; el perfil de test lo apaga para
 * que ninguna tarea programada toque las filas de los tests). La acción en sí
 * vive en {@link AlertaEvaluacionService#evaluar()}, que sí está siempre
 * disponible para invocarla a mano.</p>
 *
 * <p>Para que {@code @Scheduled} se dispare hace falta además el planificador
 * global ({@code es.manti.config.SchedulingConfig}, guard
 * {@code manti.scheduling.enabled}, apagado en el perfil de test): los dos
 * interruptores son independientes — planificador apagado ⇒ no se dispara nada;
 * aquí apagado ⇒ el planificador corre pero esta tarea no existe. El envío de
 * los avisos es del despachador del outbox ({@code manti.notificaciones.enabled}),
 * un tercer interruptor independiente.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "manti.alertas", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AlertaEvaluacionJob {

    private final AlertaEvaluacionService evaluacion;

    /** {@code fixedDelay}: la siguiente pasada empieza 15 min después de acabar. */
    @Scheduled(fixedDelayString = "${manti.alertas.cadencia-ms:900000}")
    public void evaluar() {
        log.debug("Evaluación programada de alertas de búsqueda");
        evaluacion.evaluar();
    }
}
