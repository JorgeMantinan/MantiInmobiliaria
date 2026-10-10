package es.manti.usuarios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Token de restablecimiento de contraseña (issue #57).
 *
 * <p>Patrón idéntico al refresh token: el cliente sólo recibe un token opaco
 * de 32 bytes (base64url) y en BD se guarda SÓLO su hash SHA-256, de modo que
 * un volcado de la tabla no permite canjear ningún enlace. Caduca a los 15
 * minutos y es de UN SOLO uso ({@link #usadoEn} se rellena en el canje).</p>
 *
 * <p>Al emitir un token nuevo se borran los anteriores del mismo usuario
 * ({@code eliminarTodosDeUsuario}), así que como mucho existe UN enlace vivo
 * por cuenta: el último emitido es el único válido y la tabla no crece.</p>
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** SHA-256 en hexadecimal (64 chars) del token en claro. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** {@code null} mientras el token no se canjea; lo rellena el canje. */
    @Column(name = "usado_en")
    private Instant usadoEn;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    public boolean estaUsado() {
        return usadoEn != null;
    }

    public boolean estaExpirado() {
        return expiresAt.isBefore(Instant.now());
    }
}
