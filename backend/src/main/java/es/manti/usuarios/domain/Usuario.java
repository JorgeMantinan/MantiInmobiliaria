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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
