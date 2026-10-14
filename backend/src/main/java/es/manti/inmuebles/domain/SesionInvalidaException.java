package es.manti.inmuebles.domain;

/**
 * El token autenticó correctamente pero su {@code sub} no es el identificador
 * numérico de un usuario → 401 ProblemDetail.
 *
 * <p>Mismo criterio que {@code es.manti.swipes.domain.SesionInvalidaException}:
 * sólo puede ocurrir con un token emitido con otra clave o con un usuario
 * borrado; se responde 401 (renovar sesión) en vez de 500 y sin filtrar
 * detalles internos.</p>
 */
public class SesionInvalidaException extends RuntimeException {

    public SesionInvalidaException() {
        super("Sesión inválida; vuelva a autenticarse");
    }
}
