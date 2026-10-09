package es.manti.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita el planificador global de Spring ({@code @Scheduled}) para el
 * despachador del outbox de notificaciones y la purga diaria de refresh
 * tokens.
 *
 * <p><b>Guard propio {@code manti.scheduling.enabled} (default true)</b>,
 * independiente de las tareas que programa: apagar aquí no apaga el envío
 * (su interruptor es {@code manti.notificaciones.enabled}) ni la purga
 * ({@code manti.purgas.enabled}), y al revés cada tarea puede estar encendida
 * con el planificador apagado (se invoca a mano). Antes el guard era
 * {@code manti.notificaciones.programado}, así que desacoplaba la purga de
 * refresh_tokens del disparo de notificaciones (issue #8, revisión del PR).</p>
 *
 * <p>Sólo el perfil de test lo apaga: sin planificador ninguna tarea se
 * dispara sola y los tests invocan el despachador y la purga a mano.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "manti.scheduling", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
