package es.manti.profesionales.domain;

import java.util.Collection;
import java.util.List;

/**
 * Puerto de persistencia del catálogo de gremios. Implementado en infra por
 * Spring Data JPA (patrón idéntico al de inmuebles, swipes y usuarios).
 */
public interface GremioRepository {

    /** Catálogo completo ordenado por nombre (12 filas: se cachea en la API 60 s). */
    List<Gremio> todos();

    /**
     * Gremios cuyo slug coincida con los pedidos. La comparación es
     * insensible a acentos/mayúsculas (el contrato de slugs admite
     * {@code albañil} y {@code albanil}); el servicio comprueba que la
     * respuesta cubre TODOS los slugs pedidos y si no, devuelve 400.
     */
    List<Gremio> porSlugs(Collection<String> slugs);
}
