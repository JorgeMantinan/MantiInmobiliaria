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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Solicitud de servicio enviada por un usuario del portal: "necesito un
 * fontanero en Talavera". Es el AGREGADO que dispara las notificaciones del
 * outbox (misma transacción, skill microservicios).
 */
@Entity
@Table(name = "solicitudes")
@Getter
@NoArgsConstructor
public class Solicitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_contacto", nullable = false, length = 120)
    private String nombreContacto;

    @Column(name = "email_contacto", nullable = false, length = 255)
    private String emailContacto;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "municipio", nullable = false, length = 120)
    private String municipio;

    @Column(name = "descripcion", nullable = false, columnDefinition = "text")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 8)
    private EstadoSolicitud estado;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "solicitud_gremio",
            joinColumns = @JoinColumn(name = "solicitud_id"),
            inverseJoinColumns = @JoinColumn(name = "gremio_id"))
    private Set<Gremio> gremios = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Única vía de alta: estado {@link EstadoSolicitud#NUEVA} y gremios pedidos. */
    public static Solicitud nueva(String nombreContacto, String emailContacto, String telefono,
                                  String municipio, String descripcion, Collection<Gremio> gremios) {
        Solicitud solicitud = new Solicitud();
        solicitud.nombreContacto = nombreContacto;
        solicitud.emailContacto = emailContacto;
        solicitud.telefono = telefono;
        solicitud.municipio = municipio;
        solicitud.descripcion = descripcion;
        solicitud.estado = EstadoSolicitud.NUEVA;
        solicitud.gremios.addAll(gremios);
        return solicitud;
    }
}
