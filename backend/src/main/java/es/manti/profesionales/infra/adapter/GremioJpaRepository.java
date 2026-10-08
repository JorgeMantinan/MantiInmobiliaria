package es.manti.profesionales.infra.adapter;

import es.manti.profesionales.domain.Gremio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Repositorio JPA del catálogo de gremios (implementación interna del puerto). */
public interface GremioJpaRepository extends JpaRepository<Gremio, Long> {

    List<Gremio> findAllByOrderByNombreAsc();
}
