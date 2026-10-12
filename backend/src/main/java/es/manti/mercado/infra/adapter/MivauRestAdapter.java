package es.manti.mercado.infra.adapter;

import es.manti.mercado.domain.FuenteMivauNoDisponibleException;
import es.manti.mercado.domain.FuenteMivauPort;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Adaptador de la única llamada de red del sistema: la descarga de los CSV
 * oficiales del MIVAU.
 *
 * <p><b>Timeouts propios de 30 s de conexión y 60 s de lectura</b>, deliberadamente
 * mayores que los globales de la app (3 s / 5 s): el fichero de alquiler pesa
 * 37,8 MB y tarda varios segundos incluso en buena conexión. Se construye un
 * {@code RestClient} aparte para no tocar el auto-configurado, que sí debe
 * seguir castigando rápido a los proveedores cortos (Brevo, Telegram).</p>
 *
 * <p>El cuerpo se entrega como {@link java.io.Reader} y se consume en
 * streaming: en ningún momento el fichero entero vive en el heap.</p>
 */
@Component
public class MivauRestAdapter implements FuenteMivauPort {

    /** CDN público del Ministerio; sin credenciales y sin parámetros. */
    static final String URL_ALQUILER_MUNICIPAL =
            "https://cdn.mivau.gob.es/portal-web-mivau/Datos_MIVAU/CSV/VDP001_01.csv";

    static final String URL_VALOR_TASADO =
            "https://cdn.mivau.gob.es/portal-web-mivau/Datos_MIVAU/CSV/VDP006_01.csv";

    private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(30);
    private static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(60);

    private final RestClient restClient;

    public MivauRestAdapter() {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(TIMEOUT_CONEXION, TIMEOUT_LECTURA);
        this.restClient = RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    @Override
    public void conAlquilerMunicipal(ConsumidorCsv consumidor) {
        descargar(URL_ALQUILER_MUNICIPAL, "alquiler municipal (VDP001)", consumidor);
    }

    @Override
    public void conVentaProvincial(ConsumidorCsv consumidor) {
        descargar(URL_VALOR_TASADO, "valor tasado provincial (VDP006)", consumidor);
    }

    private void descargar(String url, String descripcion, ConsumidorCsv consumidor) {
        try {
            // exchange() en lugar de retrieve(): queremos el InputStream crudo
            // y leer el estado a mano (el errorHandler de retrieve sólo sirve
            // para cuerpos ya materializados).
            restClient.get().uri(url).exchange((solicitud, respuesta) -> {
                HttpStatusCode estado = respuesta.getStatusCode();
                if (!estado.is2xxSuccessful()) {
                    throw new FuenteMivauNoDisponibleException(
                            "El MIVAU ha respondido " + estado.value() + " al descargar el CSV de " + descripcion);
                }
                try (InputStream cuerpo = respuesta.getBody()) {
                    consumidor.procesar(
                            new BufferedReader(new InputStreamReader(cuerpo, StandardCharsets.UTF_8)));
                }
                return null;
            });
        } catch (FuenteMivauNoDisponibleException e) {
            throw e;
        } catch (RestClientException e) {
            throw new FuenteMivauNoDisponibleException(
                    "No se ha podido descargar el CSV de " + descripcion + " del MIVAU", e);
        }
    }
}
