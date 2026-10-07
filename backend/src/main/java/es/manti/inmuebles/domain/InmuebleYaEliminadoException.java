package es.manti.inmuebles.domain;

/**
 * El backoffice intenta escribir sobre un anuncio que ya fue eliminado
 * (soft-delete): 409 {@code inmueble-ya-eliminado}.
 *
 * <p>Sólo lo lanzan los casos de uso de ESCRITURA; la API pública sigue
 * devolviendo 404 indistinguible de un inexistente (no revela borrados).</p>
 */
public class InmuebleYaEliminadoException extends RuntimeException {

    public InmuebleYaEliminadoException(Long id) {
        super("Inmueble " + id + " ya eliminado");
    }
}
