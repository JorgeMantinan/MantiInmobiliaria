package es.manti.profesionales.infra.adapter;

import es.manti.profesionales.domain.EstadoNotificacion;
import es.manti.profesionales.domain.NotificacionOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/** Repositorio JPA del outbox (implementación interna del puerto). */
public interface NotificacionOutboxJpaRepository extends JpaRepository<NotificacionOutbox, Long> {

    /**
     * Lote del despachador, en el orden exacto de
     * {@code idx_outbox_pendiente (creado_en, id) WHERE estado='PENDIENTE'}:
     * Index Scan sin Sort.
     */
    @Query("""
            SELECT f FROM NotificacionOutbox f
            WHERE f.estado = :estado
            ORDER BY f.creadoEn ASC, f.id ASC
            """)
    List<NotificacionOutbox> pendientes(@Param("estado") EstadoNotificacion estado,
                                        Pageable pageable);

    /** UPDATE masivo (sin cargar la entidad): sólo la fila marcada. */
    @Modifying
    @Query("""
            UPDATE NotificacionOutbox f
            SET f.estado = :estado, f.intentos = :intentos, f.enviadoEn = :enviadoEn,
                f.ultimoError = null
            WHERE f.id = :id
            """)
    int marcarEnviada(@Param("id") Long id,
                      @Param("estado") EstadoNotificacion estado,
                      @Param("intentos") Short intentos,
                      @Param("enviadoEn") Instant enviadoEn);

    @Modifying
    @Query("""
            UPDATE NotificacionOutbox f
            SET f.estado = :estado, f.intentos = :intentos, f.ultimoError = :ultimoError
            WHERE f.id = :id
            """)
    int marcarFalla(@Param("id") Long id,
                    @Param("estado") EstadoNotificacion estado,
                    @Param("intentos") Short intentos,
                    @Param("ultimoError") String ultimoError);
}
