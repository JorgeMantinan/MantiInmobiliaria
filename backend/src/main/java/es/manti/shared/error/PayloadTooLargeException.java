package es.manti.shared.error;

/**
 * El cuerpo de la petición superó el máximo permitido (256 KiB) al SER LEÍDO.
 * La lanza el stream contado de {@code BodySizeLimitFilter}, de modo que el
 * límite también alcanza a los cuerpos chunked (sin {@code Content-Length}
 * declarada), que el chequeo por cabecera no puede medir.
 * {@link GlobalExceptionHandler} la responde como 413 ProblemDetail con el
 * MISMO type que el rechazo por cabecera ({@code payload-too-large}).
 */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException() {
        super("El cuerpo de la petición supera el máximo permitido (256 KiB)");
    }
}
