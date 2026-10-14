package es.manti.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Guarda de configuración del perfil prod (issues #6 y #5): el backend va
 * DETRÁS del proxy de Vercel, que es quien sobrescribe {@code X-Forwarded-For}
 * con la IP real. Si alguien configura
 * {@code FORWARD_HEADERS_STRATEGY=none} en prod, el rate limit por IP
 * ({@code AuthRateLimitFilter}) y la auditoría de logins se vuelven inútiles
 * —todas las peticiones parecen venir del proxy— y la cookie de refresh
 * perdería el flag Secure en la cabecera Set-Cookie (X-Forwarded-Proto). En
 * lugar de fallar en silencio, el arranque FALLA con un mensaje accionable.
 *
 * <p>La variable {@code FORWARD_HEADERS_STRATEGY} sin definir es VÁLIDA: el
 * perfil prod la resuelve a {@code framework} por defecto
 * ({@code application-prod.yml}); la guarda sólo rechaza el {@code none}
 * explícito.</p>
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ForwardHeadersGuardConfig {

    private final Environment environment;

    @PostConstruct
    void validarForwardHeadersEnProd() {
        String estrategia = environment.getProperty("server.forward-headers-strategy", "");
        // Allowlist estricta (revisión PR #102): el filtro de forward headers
        // SÓLO se registra con el valor exacto "framework"; "" (env vacía),
        // "none" y cualquier typo dejan remoteAddr apuntando al proxy — el
        // efecto es el mismo que "none", así que se rechazan todos.
        String normalizada = estrategia.trim();
        if (!"framework".equalsIgnoreCase(normalizada) && !"native".equalsIgnoreCase(normalizada)) {
            throw new IllegalStateException(
                    "server.forward-headers-strategy='" + estrategia + "' NO es válido en el "
                            + "perfil prod (valores: framework, native). El backend va detrás del "
                            + "proxy de Vercel y sin ForwardedHeaderFilter el rate limit por IP "
                            + "ve la IP del proxy (todas las peticiones comparten ventana) y el "
                            + "flag X-Forwarded-Proto se pierde. Quite la variable (el default "
                            + "del perfil prod es 'framework').");
        }
        log.info("Forward headers en prod: server.forward-headers-strategy={}", normalizada);
    }
}
