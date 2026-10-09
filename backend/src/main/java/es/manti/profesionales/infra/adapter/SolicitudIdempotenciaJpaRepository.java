package es.manti.profesionales.infra.adapter;

import es.manti.profesionales.domain.SolicitudIdempotencia;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio JPA de las claves de idempotencia (implementación interna del puerto). */
public interface SolicitudIdempotenciaJpaRepository
        extends JpaRepository<SolicitudIdempotencia, String> {
}
