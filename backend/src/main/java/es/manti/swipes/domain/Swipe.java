package es.manti.swipes.domain;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Decisión de un usuario sobre un inmueble. UNA fila por (usuario, inmueble)
 * — la constraint {@code uq_swipes_usuario_inmueble} lo garantiza — porque el
 * swipe es el <b>estado actual</b> de la decisión y no un histórico: repetir la
 * misma decisión no escribe nada y cambiar de opinión actualiza la fila.
 *
 * <p>Las claves son {@code Long} a propósito ({@code usuario_id},
 * {@code inmueble_id}) y NO asociaciones: el módulo {@code swipes} no importa el
 * dominio de {@code usuarios} ni el de {@code inmuebles} (regla ArchUnit). Las
 * FK reales viven en la migración {@code V3__swipes.sql} con
 * {@code ON DELETE CASCADE}.</p>
 *
 * <p>Sin {@code @Setter}: el estado sólo cambia por {@link #cambiarDecision(DecisionSwipe)}.</p>
 */
@Entity
@Table(name = "swipes")
@Getter
@NoArgsConstructor
public class Swipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "inmueble_id", nullable = false)
    private Long inmuebleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 10)
    private DecisionSwipe decision;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Factory de alta: única vía de crear una fila (garantiza la unicidad por pareja). */
    public static Swipe nuevo(Long usuarioId, Long inmuebleId, DecisionSwipe decision) {
        Swipe swipe = new Swipe();
        swipe.usuarioId = usuarioId;
        swipe.inmuebleId = inmuebleId;
        swipe.decision = decision;
        return swipe;
    }

    /**
     * Cambio de opinión (LIKE &harr; NOPE): actualiza la decisión y dispara el
     * {@code @UpdateTimestamp} de {@code actualizado_en}.
     */
    public void cambiarDecision(DecisionSwipe nueva) {
        this.decision = nueva;
    }
}
