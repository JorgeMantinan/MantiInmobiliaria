package es.manti.profesionales.infra.adapter;

import es.manti.profesionales.application.NotificacionesProperties;
import es.manti.profesionales.domain.EmailNotificadorPort;
import es.manti.profesionales.domain.NotificacionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador de email vía <b>Brevo</b> (API SMTP v3).
 *
 * <p>Anti-corruption layer: el dominio sólo conoce {@link EmailNotificadorPort};
 * el payload, la cabecera {@code api-key} y las casuísticas de error viven
 * aquí. La clave NUNCA se escribe en un log ni en {@code ultimo_error}.</p>
 *
 * <p>Códigos de error: faltan credencial o el proveedor rechaza la petición
 * (4xx) → fallo <b>definitivo</b> (reintentar no lo arregla); 5xx o red →
 * fallo <b>reintentable</b> hasta 3 intentos.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BrevoEmailAdapter implements EmailNotificadorPort {

    static final String URL_ENVIO = "https://api.brevo.com/v3/smtp/email";

    private final RestClient notificacionesRestClient;
    private final NotificacionesProperties propiedades;
    private final ObjectMapper objectMapper;

    @Override
    public void enviar(String destino, String asunto, String html) {
        NotificacionesProperties.Brevo brevo = propiedades.brevo();
        if (brevo == null || !brevo.tieneCredencial()) {
            throw NotificacionException.definitiva("BREVO_API_KEY sin configurar");
        }
        String cuerpo = objectMapper.writeValueAsString(payload(destino, asunto, html, brevo));
        try {
            notificacionesRestClient.post()
                    .uri(URL_ENVIO)
                    .header("api-key", brevo.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw clasificar(e);
        }
        log.debug("Email enviado a {}", destino);
    }

    private static Map<String, Object> payload(String destino, String asunto, String html,
                                               NotificacionesProperties.Brevo brevo) {
        Map<String, Object> remitente = new LinkedHashMap<>();
        remitente.put("email", brevo.fromEmail());
        remitente.put("name", brevo.fromName());
        Map<String, Object> destinatario = new LinkedHashMap<>();
        destinatario.put("email", destino);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("sender", remitente);
        cuerpo.put("to", List.of(destinatario));
        cuerpo.put("subject", asunto);
        cuerpo.put("htmlContent", html);
        return cuerpo;
    }

    /** 4xx = petición malformada o credencial rechazada (no se reintenta). */
    private static NotificacionException clasificar(RestClientException e) {
        int estado = e instanceof RestClientResponseException r
                ? r.getStatusCode().value()
                : 0;
        if (estado >= 400 && estado < 500) {
            return NotificacionException.definitiva("Brevo rechazó la petición (HTTP " + estado + ")");
        }
        if (estado > 0) {
            return NotificacionException.reintentable("Brevo no disponible (HTTP " + estado + ")");
        }
        return NotificacionException.reintentable(
                "Error de red con Brevo (" + e.getClass().getSimpleName() + ")");
    }
}
