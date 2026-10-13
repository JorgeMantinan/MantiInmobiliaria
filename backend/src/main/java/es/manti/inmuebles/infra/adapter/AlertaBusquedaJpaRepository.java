package es.manti.inmuebles.infra.adapter;

import es.manti.inmuebles.domain.AlertaBusqueda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Repositorio JPA de {@code alertas_busqueda} (implementación interna del puerto). */
public interface AlertaBusquedaJpaRepository extends JpaRepository<AlertaBusqueda, Long> {

    /** Listado del dueño, de más reciente a más antigua. */
    List<AlertaBusqueda> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);

    /** Owner-scoped: si el id es de otro usuario, vacío (el servicio responde 404). */
    Optional<AlertaBusqueda> findByIdAndUsuarioId(Long id, Long usuarioId);

    long countByUsuarioId(Long usuarioId);

    /** Barrido del job: sólo activas, en orden estable de evaluación. */
    List<AlertaBusqueda> findByActivaTrueOrderByIdAsc();
}
