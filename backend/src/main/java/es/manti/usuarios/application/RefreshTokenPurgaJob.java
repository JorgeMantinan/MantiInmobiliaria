package es.manti.usuarios.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purga programada de refresh tokens caducados (issue #8).
 *
 * <p><b>Diario a las 04:17</b> (fuera de hora punta) y sólo si
 * {@code manti.purgas.enabled=true} (default; el perfil de test lo apaga para
 * que ninguna tarea programada toque las filas de los tests). La acción en sí
 * vive en {@link RefreshTokenPurgaService}, que sí está siempre disponible.</p>
 *
 * <p>Para que {@code @Scheduled} se dispare hace falta el planificador global
 * ({@code es.manti.config.SchedulingConfig}, condicionado a
 * {@code manti.notificaciones.programado}, apagado en el perfil de test): si se
 * apaga ese interruptor no corre NINGUNA tarea programada, tampoco esta.</p>
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

    /** Cron: segundo 0, minuto 17, hora 4, todos los días → 04:17. */
    @Scheduled(cron = "0 17 4 * * *")
    public void purgar() {
        log.debug("Purga programada de refresh tokens");
        purgas.purgar();
    }
}
