package es.manti.usuarios.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executor propio del envío de enlaces de restablecimiento de contraseña
 * (issue #57, revisión del PR #64).
 *
 * <p>El 202 del endpoint no puede esperar a Brevo: con el envío en un
 * executor acotado la petición devuelve ya y la llamada externa (3 s/5 s de
 * timeout) corre en su hilo. Dos hilos fijos y cola de 100 bastan para el
 * volumen de una inmobiliaria; con la cola llena,
 * {@link ThreadPoolExecutor.CallerRunsPolicy} aplica backpressure en el
 * propio hilo de la petición en vez de descartar el email.</p>
 *
 * <p>{@code @EnableAsync} vive aquí: sólo este executor y el listener de
 * restablecimiento usan {@code @Async} en todo el monolito.</p>
 */
@Configuration
@EnableAsync
public class PasswordResetAsyncConfig {

    /**
     * El nombre del bean (el del método) es el mismo que referencia
     * {@code @Async("passwordResetEmail")} en
     * {@code es.manti.usuarios.application.PasswordResetEmailSender}: si se
     * renombra uno hay que renombrar el otro.
     */
    @Bean("passwordResetEmail")
    ThreadPoolTaskExecutor passwordResetEmail() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("reset-email-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Un email en vuelo al parar el proceso: esperar unos segundos a que
        // termine antes de cerrar el executor.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
