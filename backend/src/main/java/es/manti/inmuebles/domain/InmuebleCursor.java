package es.manti.inmuebles.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Cursor opaco de la paginación keyset (NUNCA offset en el feed público).
 *
 * <p>Formato: {@code Base64url(ORDEN|valor|id)} donde {@code valor} es el
 * {@code created_at} ISO-8601 en el orden por defecto o el precio en los órdenes
 * por precio; {@code id} es el tie-break para valores repetidos. El campo
 * {@code ORDEN} viaja dentro del cursor para detectar cursors emitidos con otro
 * {@code sort} (se rechazan con 400 en vez de devolver páginas inconsistentes).</p>
 *
 * @param orden   orden con el que se emitió el cursor (de coincidir con el actual)
 * @param creadoEn created_at del último elemento de la página (sólo CREADO_DESC)
 * @param precio  precio del último elemento de la página (sólo órdenes por precio)
 * @param id      id del último elemento de la página (tie-break)
 */
public record InmuebleCursor(InmuebleOrden orden, Instant creadoEn, BigDecimal precio, Long id) {

    private static final String SEPARADOR = "|";

    public String codificar() {
        String valor = orden.porPrecio() ? precio.toPlainString() : creadoEn.toString();
        String bruto = String.join(SEPARADOR, orden.name(), valor, String.valueOf(id));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bruto.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodifica y valida un cursor recibido por query param.
     *
     * @param base64       cursor opaco del cliente
     * @param ordenActual  orden pedido en ESTA petición
     * @throws CursorInvalidoException si no es base64, no tiene el formato o el
     *                                 orden no coincide con el pedido
     */
    public static InmuebleCursor decodificar(String base64, InmuebleOrden ordenActual) {
        String bruto;
        try {
            bruto = new String(Base64.getUrlDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new CursorInvalidoException();
        }
        String[] partes = bruto.split("\\" + SEPARADOR, -1);
        if (partes.length != 3) {
            throw new CursorInvalidoException();
        }
        InmuebleOrden orden;
        try {
            orden = InmuebleOrden.valueOf(partes[0]);
        } catch (IllegalArgumentException e) {
            throw new CursorInvalidoException();
        }
        if (orden != ordenActual) {
            throw new CursorInvalidoException();
        }
        try {
            Instant creadoEn = orden.porPrecio() ? null : Instant.parse(partes[1]);
            BigDecimal precio = orden.porPrecio() ? new BigDecimal(partes[1]) : null;
            Long id = Long.valueOf(partes[2]);
            if (id <= 0) {
                throw new CursorInvalidoException();
            }
            return new InmuebleCursor(orden, creadoEn, precio, id);
        } catch (NumberFormatException | java.time.format.DateTimeParseException e) {
            throw new CursorInvalidoException();
        }
    }
}
