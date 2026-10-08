package es.manti.profesionales.domain;

import java.util.List;

/**
 * La solicitud/registro menciona gremios que no existen en el catálogo
 * → 400 con {@code errors:[{field: "gremios", ...}]}.
 */
public class GremioInexistenteException extends RuntimeException {

    public GremioInexistenteException(List<String> slugs) {
        super("Gremios inexistentes: " + String.join(", ", slugs));
    }
}
