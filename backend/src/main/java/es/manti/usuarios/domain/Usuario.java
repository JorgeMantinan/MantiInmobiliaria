package es.manti.usuarios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Cuenta de usuario. Puede haberse creado con email/password (password_hash)
 * o con Google (google_id); al menos una de las dos credenciales existe
 * (restricción ck_usuarios_credencial en V1).
 */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /** Hash BCrypt; {@code null} en cuentas creadas con Google. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    /** Identificador de Google (sub); {@code null} en cuentas email/password. */
    @Column(name = "google_id", unique = true, length = 64)
    private String googleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 16)
    private Rol rol;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    /**
     * Email verificado por su propietario o por el proveedor OIDC. Nace a
     * {@code false} en los registros locales (V5) y a {@code true} cuando la
     * cuenta se crea desde Google. Es la condición para aceptar un vínculo
     * Google (ver {@link #vincularGoogle}): cierra el pre-hijack de cuentas.
     */
    @Column(name = "email_verificado", nullable = false)
    private boolean emailVerificado;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Vincula la cuenta de Google al usuario (o la re-vincula si es el mismo
     * google_id). Guard 1: si la cuenta local nunca verificó su email se
     * rechaza con {@link CuentaGoogleSinVerificarException} — sin verificar,
     * un registro local con un email ajeno podría "interceptar" el login con
     * Google de su propietario real (pre-hijack / ATO). Guard 2: si la cuenta
     * ya estaba vinculada a OTRO google_id, se rechaza con
     * {@link CuentaGoogleConflictivaException}.
     *
     * @param googleId       identificador (sub) de Google
     * @param nombreSugerido nombre del perfil de Google; sólo se usa si el
     *                       usuario no tiene nombre propio
     */
    public void vincularGoogle(String googleId, String nombreSugerido) {
        if (!this.emailVerificado) {
            throw new CuentaGoogleSinVerificarException();
        }
        if (this.googleId != null && !this.googleId.equals(googleId)) {
            throw new CuentaGoogleConflictivaException();
        }
        this.googleId = googleId;
        if ((this.nombre == null || this.nombre.isBlank())
                && nombreSugerido != null && !nombreSugerido.isBlank()) {
            this.nombre = nombreSugerido.trim();
        }
    }
}
