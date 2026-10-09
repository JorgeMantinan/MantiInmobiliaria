package es.manti.usuarios.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purga programada de refresh tokens caducados (issue #8).
 *
 * <p><b>Diario a las 04:17 en {@code Europe/Madrid}</b> (fuera de hora punta;
 * la zona se fija para que la hora no dependa del TZ del host ni del
 * contenedor) y sólo si {@code manti.purgas.enabled=true} (default; el perfil
 * de test lo apaga para que ninguna tarea programada toque las filas de los
 * tests). La acción en sí vive en {@link RefreshTokenPurgaService}, que sí
 * está siempre disponible.</p>
 *
 * <p>Para que {@code @Scheduled} se dispare hace falta además el planificador
 * global ({@code es.manti.config.SchedulingConfig}, guard propio
 * {@code manti.scheduling.enabled}, apagado en el perfil de test): los dos
 * interruptores son independientes — planificador apagado ⇒ no se dispara
 * nada; aquí apagado ⇒ el planificador corre pero esta tarea no existe.</p>
 *
 * <p>Uso manual / diagnóstico: invocar {@link RefreshTokenPurgaService#purgar()}
 * desde un test o un endpoint interno; el DELETE es idempotente.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "manti.purgas", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class RefreshTokenPurgaJob {

    private final RefreshTokenPurgaService purgas;

    /** Cron: segundo 0, minuto 17, hora 4, todos los días → 04:17 (Madrid). */
    @Scheduled(cron = "0 17 4 * * *", zone = "Europe/Madrid")
    public void purgar() {
        log.debug("Purga programada de refresh tokens");
        purgas.purgar();
    }
}
