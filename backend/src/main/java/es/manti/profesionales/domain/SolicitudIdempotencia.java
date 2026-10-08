package es.manti.profesionales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Registro de una clave {@code Idempotency-Key} ya usada en el alta pública de
 * solicitudes ({@code POST /api/v1/solicitudes}).
 *
 * <p>Se escribe en la MISMA transacción que la solicitud: si el commit no llega,
 * tampoco queda la clave. La clave es la PK, de modo que dos reintentos
 * concurrentes no pueden crear dos solicitudes (el segundo choca y la API
 * responde 409 de integridad).</p>
 */
@Entity
@Table(name = "solicitudes_idempotencia")
@Getter
@NoArgsConstructor
public class SolicitudIdempotencia {

    /** Cabecera {@code Idempotency-Key} del cliente (máx. 64 caracteres). */
    @Id
    @Column(name = "clave", length = 64)
    private String clave;

    /** Solicitud creada por la petición original. */
    @Column(name = "solicitud_id", nullable = false)
    private Long solicitudId;

    /** Código HTTP de la respuesta original (201): es lo que devuelve el replay. */
    @Column(name = "codigo", nullable = false)
    private Integer codigo;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    public SolicitudIdempotencia(String clave, Long solicitudId, int codigo) {
        this.clave = clave;
        this.solicitudId = solicitudId;
        this.codigo = codigo;
    }
}
