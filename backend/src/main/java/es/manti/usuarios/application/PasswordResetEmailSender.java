package es.manti.usuarios.application;

import es.manti.shared.notificaciones.EmailNotificadorPort;
import es.manti.shared.notificaciones.NotificacionesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;

/**
 * Envío del enlace de restablecimiento de contraseña (issue #57, revisión del
 * PR #64). Un ÚNICO componente con dos puertas de entrada para que el cuerpo
 * del email y el guard de notificaciones sean idénticos en ambos caminos:
 *
 * <ul>
 *   <li><b>asíncrono (producción, por defecto)</b>: el servicio publica
 *       {@link EnlacePasswordReset} dentro de la transacción que crea el
 *       token; este listener se dispara SÓLO tras el commit
 *       ({@link TransactionalEventListener}) y en el executor acotado
 *       {@code passwordResetEmail} ({@link Async}) — el 202 del endpoint no
 *       espera a Brevo y cierra el oráculo de timing;</li>
 *   <li><b>síncrono ({@code auth.password-reset.sync=true}, perfil de
 *       test)</b>: el servicio llama a {@link #enviar} en línea para que los
 *       tests puedan verificar el envío con el mock del puerto sin carreras
 *       de hilos.</li>
 * </ul>
 *
 * <p>El envío ocurre SIEMPRE fuera de transacción y con los timeouts del
 * cliente de notificaciones (3 s/5 s). Un fallo del proveedor se loguea (con
 * el email enmascarado, sin PII) y no propaga: el 202 ya salió.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetEmailSender {

    private static final String ASUNTO = "Restablece tu contraseña de MantiInmobiliaria";

    private final EmailNotificadorPort correo;
    private final NotificacionesProperties notificaciones;
    private final PasswordResetProperties properties;

    /**
     * Camino ASÍNCRONO: listener del evento de emisión. Se ejecuta tras el
     * commit, en el executor {@code passwordResetEmail}, sin transacción.
     */
    @Async("passwordResetEmail")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviarAsincrono(EnlacePasswordReset evento) {
        enviar(evento.email(), evento.token());
    }

    /**
     * Envía el enlace al email dado. Idéntico en ambos caminos; sólo lo
     * difiere cuándo se invoca.
     *
     * <p>Respeta el interruptor global {@code manti.notificaciones.enabled}
     * (Bajo, revisión del PR #64): apagado (local/CI por defecto), el token
     * ya creado no se envía y sólo queda un warn — el usuario puede volver a
     * pedir el enlace cuando se reenciende el switch. El guard vive AQUÍ, en
     * la puerta única de ambos caminos, para que sync y async no se diverjan.
     * </p>
     */
    public void enviar(String email, String token) {
        if (!notificaciones.enabled()) {
            log.warn("Notificaciones apagadas (manti.notificaciones.enabled=false): token de "
                    + "restablecimiento creado para {} pero sin enviar", mascararEmail(email));
            return;
        }
        String enlace = properties.frontendUrl() + "/restablecer?token=" + token;
        try {
            correo.enviar(email, ASUNTO, cuerpoEmail(enlace));
            log.debug("Enlace de restablecimiento enviado a {}", email);
        } catch (RuntimeException fallo) {
            log.warn("No se pudo enviar el email de restablecimiento a {}: {}", mascararEmail(email),
                    fallo.getMessage(), fallo);
        }
    }

    /**
     * Cuerpo del email. Sólo lleva datos propios (token base64url: A-Z, a-z,
     * 0-9, {@code -}, {@code _}; y {@code FRONTEND_URL}, de configuración):
     * no hay entrada de usuario que escapar. El TTL del enlace se deriva de
     * {@code auth.password-reset.ttl}: si alguien lo cambia a 2 horas, el
     * email no miente.
     */
    String cuerpoEmail(String enlace) {
        return """
                <p>Has pedido restablecer tu contraseña de MantiInmobiliaria.</p>
                <p><a href="%s">Haz clic aquí para elegir una contraseña nueva</a>.</p>
                <p>El enlace caduca en %s y sólo puede usarse una vez.</p>
                <p>Si no has sido tú, ignora este mensaje: tu contraseña no cambiará.</p>
                """.formatted(enlace, textoTtl(properties.ttl()));
    }

    /**
     * TTL del enlace en texto para el email: horas cuando es múltiplo exacto
     * de una hora ({@code 2h → "2 horas"}), minutos en el resto
     * ({@code 15m → "15 minutos"}). Un TTL por debajo del minuto se
     * redondea hacia arriba a 1 minuto: el email nunca dice "0 minutos".
     */
    static String textoTtl(Duration ttl) {
        long minutos = Math.max(1, ttl.toMinutes());
        if (minutos >= 60 && minutos % 60 == 0) {
            long horas = minutos / 60;
            return horas + (horas == 1 ? " hora" : " horas");
        }
        return minutos + (minutos == 1 ? " minuto" : " minutos");
    }

    /**
     * Log con el dominio del email y la inicial enmascarados
     * ({@code a***@ejemplo.com}): los avisos de este flujo no deben dejar
     * direcciones completas en los logs (PII).
     */
    static String mascararEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int arroba = email.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        return email.charAt(0) + "***@" + email.substring(arroba + 1);
    }
}
