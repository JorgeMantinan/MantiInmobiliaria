package es.manti.profesionales.infra.config;

import es.manti.profesionales.application.NotificacionesProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Cliente HTTP propio para los proveedores de notificaciones (Brevo y
 * Telegram).
 *
 * <p><b>Timeouts obligatorios</b> (skill rendimiento): 3 s de conexión y 5 s
 * de lectura. Sin ellos, un proveedor caído dejaría hilos esperando
 * indefinidamente y el despachador no podría completar su pasada.</p>
 *
 * <p>Un único cliente compartido, SIN {@code baseUrl}: cada adaptador usa la
 * URL absoluta de su proveedor (hosts fijos en código, nunca entrados por el
 * usuario → sin riesgo de SSRF). Sólo se envían y reciben {@code String}:
 * la serialización JSON la hace cada adaptador con el Jackson de la app.</p>
 */
@Configuration
@EnableConfigurationProperties(NotificacionesProperties.class)
public class NotificacionesHttpConfig {

    /** Timeout de conexión al proveedor. */
    static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(3);

    /** Timeout de lectura de la respuesta (el getUpdates de Telegram puede tardar). */
    static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(5);

    @Bean
    RestClient notificacionesRestClient() {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT_CONEXION)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(TIMEOUT_LECTURA);
        // RestClient.builder() registra los conversores por defecto (String y JSON)
        return RestClient.builder().requestFactory(factory).build();
    }
}
