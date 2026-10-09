package es.manti.profesionales.infra.adapter;

import es.manti.profesionales.domain.EstadoProfesional;
import es.manti.profesionales.domain.Profesional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Repositorio JPA de profesionales (implementación interna del puerto). */
public interface ProfesionalJpaRepository extends JpaRepository<Profesional, Long> {

    boolean existsByEmail(String email);

    /**
     * Un ACTIVO que comparta varios gremios pedidos aparece UNA sola vez:
     * {@code SELECT DISTINCT} sobre las columnas de la entidad (sin fetch de la
     * colección) sí desduplica en SQL.
     */
    @Query("""
            SELECT DISTINCT p FROM Profesional p
            JOIN p.gremios g
            WHERE p.estado = :estado AND g.id IN :gremioIds
            """)
    List<Profesional> activosCompartiendoGremios(@Param("estado") EstadoProfesional estado,
                                                 @Param("gremioIds") Collection<Long> gremioIds);

    List<Profesional> findAllByOrderByCreadoEnDesc();

    List<Profesional> findByEstadoOrderByCreadoEnDesc(EstadoProfesional estado);

    /** (id, slug) de los gremios de esos profesionales en UNA sola query (anti-N+1). */
    @Query("""
            SELECT p.id, g.slug FROM Profesional p
            JOIN p.gremios g
            WHERE p.id IN :ids
            """)
    List<Object[]> slugsDeGremios(@Param("ids") Collection<Long> ids);
}
