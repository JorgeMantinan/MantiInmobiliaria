package es.manti.swipes.domain;

/**
 * El token autenticó correctamente pero su {@code sub} no es el identificador
 * numérico de un usuario → 401 ProblemDetail.
 *
 * <p>Sólo puede ocurrir con un token emitido por otra clave (o con un usuario
 * borrado): se responde 401 para que el cliente renueve la sesión en vez de un
 * 500, y sin filtrar ningún detalle interno.</p>
 */
public class SesionInvalidaException extends RuntimeException {

    public SesionInvalidaException() {
        super("Sesión inválida; vuelva a autenticarse");
    }
}
