package es.manti.profesionales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Profesional (empresa o autónomo) que quiere recibir solicitudes.
 * Nace {@link EstadoProfesional#PENDIENTE} en el registro público y sólo un
 * ADMIN lo activa.
 *
 * <p>Los gremios son una colección {@code LAZY}: la carga diferida se hace
 * siempre dentro de la transacción (bulk JPQL en la aplicación) o se evita,
 * nunca se toca fuera de ella ({@code open-in-view: false}).</p>
 */
@Entity
@Table(name = "profesionales")
@Getter
@NoArgsConstructor
public class Profesional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "municipio", length = 120)
    private String municipio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoProfesional estado;

    /**
     * Control optimista (issue #51): Hibernate añade {@code version = ?} al
     * WHERE del UPDATE y si OTRA transacción modificó la fila entre la
     * lectura y la escritura no afecta a ninguna fila y lanza
     * {@code OptimisticLockingFailureException} (409 en el backoffice).
     * Columna {@code version bigint NOT NULL DEFAULT 0} (migración V7).
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "profesional_gremio",
            joinColumns = @JoinColumn(name = "profesional_id"),
            inverseJoinColumns = @JoinColumn(name = "gremio_id"))
    private Set<Gremio> gremios = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Única vía de alta: garantiza el estado inicial y los gremios sin duplicados. */
    public static Profesional nuevo(String nombre, String email, String telefono,
                                    String municipio, Collection<Gremio> gremios) {
        Profesional profesional = new Profesional();
        profesional.nombre = nombre;
        profesional.email = email;
        profesional.telefono = telefono;
        profesional.municipio = municipio;
        profesional.estado = EstadoProfesional.PENDIENTE;
        profesional.gremios.addAll(gremios);
        return profesional;
    }

    /**
     * Moderación del backoffice: ACTIVO o RECHAZADO (nunca vuelve a PENDIENTE).
     *
     * <p>Regla de dominio además de la validación del DTO: aunque la API sólo
     * traduce {@code ACTIVO|RECHAZADO}, ningún código puede devolver a un
     * profesional al estado inicial.</p>
     *
     * @throws EstadoProfesionalNoPermitidoException si el estado no es
     *         {@link EstadoProfesional#ACTIVO} ni {@link EstadoProfesional#RECHAZADO}
     */
    public void cambiarEstado(EstadoProfesional nuevo) {
        if (nuevo != EstadoProfesional.ACTIVO && nuevo != EstadoProfesional.RECHAZADO) {
            throw new EstadoProfesionalNoPermitidoException(nuevo);
        }
        this.estado = nuevo;
    }
}
