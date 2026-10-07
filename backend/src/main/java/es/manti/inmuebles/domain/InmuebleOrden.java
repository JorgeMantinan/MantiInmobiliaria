package es.manti.inmuebles.domain;

/**
 * Orden admitido en el listado público. Cada orden tiene su propia query (con su
 * ORDER BY y su predicado de cursor) para que el plan use la B-tree que toca.
 */
public enum InmuebleOrden {

    /** Por defecto: {@code created_at DESC, id DESC}. */
    CREADO_DESC,
    /** {@code precio ASC, id ASC}. */
    PRECIO_ASC,
    /** {@code precio DESC, id DESC}. */
    PRECIO_DESC;

    /**
     * Interpreta el query param {@code sort} de la API.
     *
     * @param sort valor crudo ({@code precio_asc}, {@code precio_desc} o null)
     * @throws ParametroInvalidoException si el valor no está admitido
     */
    public static InmuebleOrden desdeParametro(String sort) {
        if (sort == null || sort.isBlank()) {
            return CREADO_DESC;
        }
        return switch (sort) {
            case "precio_asc" -> PRECIO_ASC;
            case "precio_desc" -> PRECIO_DESC;
            default -> throw new ParametroInvalidoException("sort", "valores admitidos: precio_asc, precio_desc");
        };
    }

    public boolean porPrecio() {
        return this != CREADO_DESC;
    }
}
