package es.manti.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita el planificador de Spring para el despachador del outbox de
 * notificaciones ({@code @Scheduled(fixedDelay = 15000)}).
 *
 * <p>Se puede apagar con {@code manti.notificaciones.programado=false} (lo
 * hace el perfil de test para que los tests invoquen el despachador a mano y
 * ninguna tarea programada los ensucie): sin planificador, el envío sigue
 * siendo invocable, simplemente no se dispara solo.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "manti.notificaciones", name = "programado",
        havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
