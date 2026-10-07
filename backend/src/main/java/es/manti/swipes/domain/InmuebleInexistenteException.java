package es.manti.swipes.domain;

/**
 * Swipe sobre un inmueble que no existe o que está eliminado (soft-delete) →
 * 404 ProblemDetail. El módulo lo comprueba contra la tabla {@code inmuebles}
 * sin importar su entidad (monolito de esquema compartido; ver el javadoc de
 * {@link SwipeRepository#existeInmuebleVigente(Long)}).
 */
public class InmuebleInexistenteException extends RuntimeException {

    public InmuebleInexistenteException(Long id) {
        super("Inmueble " + id + " inexistente o eliminado");
    }
}
