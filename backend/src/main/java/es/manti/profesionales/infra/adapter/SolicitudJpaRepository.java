package es.manti.profesionales.infra.adapter;

import es.manti.profesionales.domain.Solicitud;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Repositorio JPA de solicitudes (implementación interna del puerto). */
public interface SolicitudJpaRepository extends JpaRepository<Solicitud, Long> {

    @Query("SELECT s FROM Solicitud s ORDER BY s.creadoEn DESC, s.id DESC")
    List<Solicitud> ultimas(Pageable pageable);

    /** (id, slug) de los gremios pedidos por esas solicitudes en UNA sola query. */
    @Query("""
            SELECT s.id, g.slug FROM Solicitud s
            JOIN s.gremios g
            WHERE s.id IN :ids
            """)
    List<Object[]> slugsDeGremios(@Param("ids") Collection<Long> ids);
}
