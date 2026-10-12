package es.manti.usuarios.application;

import es.manti.usuarios.domain.PasswordResetInvalidoException;
import es.manti.usuarios.domain.PasswordResetToken;
import es.manti.usuarios.domain.PasswordResetTokenRepository;
import es.manti.usuarios.domain.Usuario;
import es.manti.usuarios.domain.UsuarioNoEncontradoException;
import es.manti.usuarios.domain.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Flujo de restablecimiento de contraseña (issue #57): emisión del enlace por
 * email ({@code forgot}) y canje ({@code reset}).
 *
 * <p><b>Anti-enumeración.</b> {@link #solicitar} responde 202 SIEMPRE: si el
 * email no existe (o la cuenta no tiene password — Google-only) no se envía
 * nada, pero la respuesta es idéntica a la de una cuenta real. El endpoint no
 * distingue nunca "no existe" de "existe".</p>
 *
 * <p><b>Token.</b> 32 bytes {@link SecureRandom} → base64url (mismo patrón que
 * los códigos Google y los refresh tokens); en BD sólo vive su SHA-256, así
 * que un volcado de la tabla no permite canjear enlaces. Un solo uso y TTL
 * configurable ({@code auth.password-reset.ttl}, 15 min por defecto). Como
 * mucho hay UN enlace vivo por cuenta: si al pedirlo ya hay uno vigente no se
 * emite otro (cooldown), y al emitir uno nuevo se borran los anteriores.
 * </p>
 *
 * <p><b>Envío asíncrono (revisión del PR #64).</b> El email NO se envía en la
 * petición: dentro de la transacción que crea el token se publica el evento
 * {@link EnlacePasswordReset}, que
 * {@link PasswordResetEmailSender} consume tras el commit en un executor
 * acotado — el 202 sale ya y el tiempo de respuesta deja de revelar si la
 * cuenta existe (oráculo de timing). Con {@code auth.password-reset.sync=true}
 * (perfil de test) el envío es en línea, para poder verificarlo con el mock
 * del puerto sin carreras.</p>
 *
 * <p><b>Transacciones y BCrypt (issue #24).</b> El método NO lleva
 * {@code @Transactional}: la lectura cierra su transacción de sólo lectura,
 * el {@code encode} BCrypt (60-150 ms de CPU) corre sin transacción abierta y
 * la escritura (token) entra en una transacción propia vía
 * {@link TransactionTemplate}. El envío —sincrónico o en el executor— ocurre
 * SIEMPRE fuera de transacción.</p>
 */
@Slf4j
@Service
public class PasswordResetService {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final int BYTES_TOKEN = 32;

    private final UsuarioRepository usuarios;
    private final PasswordResetTokenRepository tokens;
    private final RefreshTokenService refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetEmailSender sender;
    private final ApplicationEventPublisher eventos;
    private final PasswordResetProperties properties;
    private final TransactionTemplate transaccion;

    public PasswordResetService(UsuarioRepository usuarios, PasswordResetTokenRepository tokens,
                                RefreshTokenService refreshTokens, PasswordEncoder passwordEncoder,
                                PasswordResetEmailSender sender, ApplicationEventPublisher eventos,
                                PasswordResetProperties properties, TransactionTemplate transaccion) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.sender = sender;
        this.eventos = eventos;
        this.properties = properties;
        this.transaccion = transaccion;
    }

    /**
     * Emite un token de restablecimiento y deja el enlace listo para enviar.
     *
     * <p>SIN transacción ni retorno condicional: un email inexistente, una
     * cuenta Google sin password o una cuenta desactivada simplemente no
     * envían correo (y no emiten token), pero el flujo termina igual — el
     * controlador responde 202 en todos los casos.</p>
     *
     * <p>Con {@code manti.notificaciones.enabled=false} (local/CI) el token
     * SÍ se crea pero no se envía (sólo un warn): el guard vive en
     * {@link PasswordResetEmailSender#enviar}, común a ambos caminos. Si la
     * cuenta se borra en la ventana entre la lectura y la transacción, se
     * aborta sin enviar y sin 404 (mismo 202).</p>
     */
    public void solicitar(String email) {
        String normalizado = normalizar(email);
        Usuario usuario = usuarios.findByEmail(normalizado).orElse(null);
        if (usuario == null || usuario.getPasswordHash() == null || !usuario.isActivo()) {
            log.debug("Restablecimiento solicitado sin envío (cuenta inexistente, sin password "
                    + "o inactiva): {}", normalizado);
            return;
        }
        if (tokens.existeVigentePara(usuario.getId(), Instant.now())) {
            // Cooldown (revisión del PR #64): ya hay un enlace vivo para esta
            // cuenta; NO se borra ni se reenvía (el usuario que perdió el
            // primer email conserva su enlace y no se spamea el proveedor).
            // El 202 del endpoint es idéntico al de una emisión nueva.
            log.debug("Restablecimiento solicitado con enlace vigente para el usuario {}: "
                    + "sin reenvío", usuario.getId());
            return;
        }
        String token = generar();
        try {
            transaccion.execute(estado -> {
                // Relectura en transacción: si la cuenta se borra aquí, no se emite token.
                Usuario vigente = usuarios.findById(usuario.getId())
                        .orElseThrow(UsuarioNoEncontradoException::new);
                // Un solo enlace vivo por cuenta: los anteriores (usados o caducados) caen.
                tokens.eliminarTodosDeUsuario(vigente.getId());
                PasswordResetToken fila = new PasswordResetToken();
                fila.setUsuario(vigente);
                fila.setTokenHash(sha256(token));
                fila.setExpiresAt(Instant.now().plus(properties.ttl()));
                tokens.save(fila);
                if (!properties.sync()) {
                    // Se publica DENTRO de la transacción a propósito: el listener
                    // AFTER_COMMIT sólo se dispara si el commit tiene éxito (una
                    // fila borrada en rollback no debe generar un email fantasma).
                    eventos.publishEvent(new EnlacePasswordReset(vigente.getEmail(), token));
                }
                return null;
            });
        } catch (UsuarioNoEncontradoException carrera) {
            // Bajo (revisión del PR #64): la cuenta se dio de baja entre la
            // lectura inicial y la relectura en transacción. Sin 404 — sería
            // un oráculo de status que revela la carrera—; se aborta sin
            // enviar y el 202 del endpoint sigue siendo el mismo.
            log.debug("Restablecimiento abortado en carrera: la cuenta {} ya no existe", normalizado);
            return;
        }
        if (properties.sync()) {
            // Camino de test: envío en línea, verificable con el mock del puerto.
            sender.enviar(usuario.getEmail(), token);
        }
    }

    /**
     * Canjea el token por una contraseña nueva y cierra TODA la sesión
     * previa: se marca el token como usado (un solo uso) y se revoca la
     * familia entera de refresh tokens.
     *
     * @throws PasswordResetInvalidoException 400 si el token no existe, está
     *                                       caducado o ya fue canjeado
     */
    public void restablecer(String tokenEnClaro, String password) {
        if (tokenEnClaro == null || tokenEnClaro.isBlank()) {
            throw new PasswordResetInvalidoException();
        }
        PasswordResetToken token = tokens.findByTokenHash(sha256(tokenEnClaro))
                .orElseThrow(PasswordResetInvalidoException::new);
        if (token.estaUsado() || token.estaExpirado()) {
            throw new PasswordResetInvalidoException();
        }
        // JOIN FETCH: el usuario ya viene resuelto con el token.
        Long usuarioId = token.getUsuario().getId();
        // BCrypt FUERA de la transacción (issue #24): 60-150 ms de CPU sin
        // retener una conexión de Hikari.
        String passwordHash = passwordEncoder.encode(password);
        transaccion.execute(estado -> {
            // Canje atómico: sólo una petición gana; la pierde recibe 400.
            if (tokens.consumir(token.getId(), Instant.now()) == 0) {
                throw new PasswordResetInvalidoException();
            }
            Usuario usuario = usuarios.findById(usuarioId)
                    .orElseThrow(UsuarioNoEncontradoException::new);
            // Bajo (revisión del PR #64): re-verificar que la cuenta sigue
            // activa EN el momento del canje. Si se desactivó después de emitir
            // el enlace, no se rota la password; 400 genérico (mismo
            // token-invalido), sin revelar el motivo real.
            if (!usuario.isActivo()) {
                throw new PasswordResetInvalidoException();
            }
            usuario.setPasswordHash(passwordHash);
            usuarios.save(usuario);
            // Revoca TODA la familia de refresh (reutiliza revokeAllFromUsuario):
            // la sesión del atacante (y la del legítimo) queda fuera.
            long revocados = refreshTokens.revocarTodosDe(usuarioId);
            log.info("Contraseña restablecida para el usuario {}; {} refresh token(s) revocado(s)",
                    usuarioId, revocados);
            return null;
        });
    }

    private static String generar() {
        byte[] bytes = new byte[BYTES_TOKEN];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static String normalizar(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
