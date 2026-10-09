package es.manti.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FASE 7 — issue #31: timeouts globales del cliente HTTP saliente
 * ({@code spring.http.clients.connect-timeout=3s / read-timeout=5s}).
 *
 * <p>En Boot 4.1 esas claves viven en el módulo {@code spring-boot-http-client}
 * (starter {@code spring-boot-restclient}): sin él no se bindean. Este test
 * comprueba tanto el módulo (existe un {@code RestClient.Builder}
 * auto-configurado que las consume) como los valores bound en
 * {@link HttpClientSettings}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class HttpClientTimeoutsTest {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void elClienteHttpAutoConfiguradoDeBootEstaDisponible() {
        assertThat(contexto.getBeanNamesForType(RestClient.Builder.class))
                .as("spring-boot-restclient debe estar en el classpath")
                .isNotEmpty();
    }

    @Test
    void losTimeoutsGlobalesEstanBindeadosA3sDeConexionY5sDeLectura() {
        HttpClientSettings settings = contexto.getBeanProvider(HttpClientSettings.class).getIfAvailable();

        assertThat(settings).as("HttpClientSettings auto-configurado").isNotNull();
        assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(settings.readTimeout()).isEqualTo(Duration.ofSeconds(5));
    }
}
