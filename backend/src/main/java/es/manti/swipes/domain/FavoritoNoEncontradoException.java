package es.manti.swipes.domain;

/**
 * Baja de un favorito que el USUARIO ACTUAL no tiene → 404 ProblemDetail.
 *
 * <p>La búsqueda va siempre filtrada por {@code usuario_id}, de modo que un
 * favorito de otro usuario responde 404 y no 403: la API no revela si ese
 * inmueble es favorito de alguien (IDOR). Un {@code NOPE} sobre el mismo
 * inmueble también responde 404: no es un favorito.</p>
 */
public class FavoritoNoEncontradoException extends RuntimeException {

    public FavoritoNoEncontradoException(Long inmuebleId) {
        super("No hay favorito sobre el inmueble " + inmuebleId);
    }
}
