package es.manti.inmuebles.infra.adapter;

import es.manti.inmuebles.domain.AlertaBusqueda;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Repositorio JPA de {@code alertas_busqueda} (implementación interna del puerto). */
public interface AlertaBusquedaJpaRepository extends JpaRepository<AlertaBusqueda, Long> {

    /** Listado del dueño, de más reciente a más antigua. */
    List<AlertaBusqueda> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);

    /** Owner-scoped: si el id es de otro usuario, vacío (el servicio responde 404). */
    Optional<AlertaBusqueda> findByIdAndUsuarioId(Long id, Long usuarioId);

    long countByUsuarioId(Long usuarioId);

    /**
     * Menor marca de agua entre las activas; {@code null} si no hay activas.
     * Recorrido por {@code idx_alertas_busqueda_activas (id) WHERE activa}:
     * el plan sólo toca las filas vivas (evidencia en la cabecera del puerto).
     */
    @Query("SELECT MIN(a.ultimoInmuebleAvisadoId) FROM AlertaBusqueda a WHERE a.activa = true")
    Long minimaMarcaActiva();

    /**
     * Lote del barrido del job (follow-up #98), en el orden del índice
     * parcial {@code idx_alertas_busqueda_activas (id) WHERE activa}: el
     * predicado {@code id > :desdeId} + {@code ORDER BY id} es un range scan
     * ascendente por el índice, sin Sort.
     *
     * <p>Bloqueo {@code FOR UPDATE SKIP LOCKED} (multi-instancia): si otra
     * instancia ha cogido ya las filas, esta query no espera ni las devuelve.
     * JPQL no tiene cláusula {@code SKIP LOCKED}, así que se expresa con el
     * {@code @Lock} de JPA y el hint de timeout {@code -2} (= skip locked en
     * Hibernate), idéntico al del lote de pendientes del outbox.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT a FROM AlertaBusqueda a
            WHERE a.activa = true AND a.id > :desdeId
            ORDER BY a.id ASC
            """)
    List<AlertaBusqueda> activasDesde(@Param("desdeId") long desdeId, Pageable pageable);
}
