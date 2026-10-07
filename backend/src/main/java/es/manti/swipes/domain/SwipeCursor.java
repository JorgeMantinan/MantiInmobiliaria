package es.manti.swipes.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Cursor opaco de la paginación keyset (nunca offset en deck ni favoritos).
 *
 * <p>Formato: {@code Base64url(ÁMBITO|valor|id)} donde {@code valor} es el
 * instante ISO-8601 de la clave de orden y {@code id} el desempate. El ámbito
 * viaja DENTRO del cursor para rechazar con 400 un cursor emitido para el otro
 * listado (mismo patrón que el campo {@code ORDEN} de
 * {@code es.manti.inmuebles.domain.InmuebleCursor}): sin esa marca, un cursor
 * de deck usado en favoritos devolvería páginas corruptas en vez de un error.</p>
 *
 * @param ambito   listado que emitió el cursor (de coincidir con el actual)
 * @param creadoEn clave temporal: {@code created_at} del inmueble (deck) o
 *                 {@code creado_en} del swipe (favoritos)
 * @param id       desempate: id del inmueble (deck) o id del swipe (favoritos)
 */
public record SwipeCursor(Ambito ambito, Instant creadoEn, Long id) {

    private static final String SEPARADOR = "|";

    /** Listado que genera el cursor; cada uno con su propia clave de orden. */
    public enum Ambito {
        /** Inmuebles activos aún no swipeados: keyset sobre {@code inmuebles(created_at, id)}. */
        DECK,
        /** Favoritos del usuario: keyset sobre {@code swipes(creado_en, id)}. */
        FAVORITOS
    }

    public String codificar() {
        String bruto = String.join(SEPARADOR, ambito.name(), creadoEn.toString(), String.valueOf(id));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bruto.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodifica y valida un cursor recibido por query param.
     *
     * @param base64      cursor opaco del cliente
     * @param ambitoActual listado pedido en ESTA petición
     * @throws CursorInvalidoException si no es base64, no tiene el formato, el
     *                                 instante o el id no son parseables, o el
     *                                 ámbito no coincide con el pedido
     */
    public static SwipeCursor decodificar(String base64, Ambito ambitoActual) {
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
        Ambito ambito;
        try {
            ambito = Ambito.valueOf(partes[0]);
        } catch (IllegalArgumentException e) {
            throw new CursorInvalidoException();
        }
        if (ambito != ambitoActual) {
            throw new CursorInvalidoException();
        }
        try {
            Instant creadoEn = Instant.parse(partes[1]);
            Long id = Long.valueOf(partes[2]);
            if (id <= 0) {
                throw new CursorInvalidoException();
            }
            return new SwipeCursor(ambito, creadoEn, id);
        } catch (NumberFormatException | java.time.format.DateTimeParseException e) {
            throw new CursorInvalidoException();
        }
    }
}
