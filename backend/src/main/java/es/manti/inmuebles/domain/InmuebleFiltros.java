package es.manti.inmuebles.domain;

import java.math.BigDecimal;

/**
 * Filtros del listado público. Todos opcionales.
 *
 * <p><b>Patrón real</b> (ver {@code InmuebleJpaRepository.FILTROS}): cada filtro
 * se emite SIEMPRE como {@code (:param IS NULL OR columna <op> :param)} en todas
 * las filas, y no se concatenan ramas según los parámetros nulos. Se hace así
 * porque:</p>
 * <ul>
 *   <li>hay UNA sola query por orden (2 órdenes con/sin cursor) en vez de 2⁷
 *       combinaciones, así el plan sigue siendo reutilizable;</li>
 *   <li>JDBC manda el parámetro con su tipo, así que la rama
 *       {@code (:param IS NULL)} se resuelve en el plan y no costea nada en la
 *       práctica.</li>
 * </ul>
 *
 * <p>Lo que NO se hace es comparar contra el literal {@code null}
 * ({@code columna = null}): eso evaluaría a {@code NULL} y excluiría filas por
 * accidente.</p>
 *
 * @param municipioPatron patrón ya construido (y ya en minúsculas) para
 *                        {@code LOWER(municipio) LIKE ?} con comodines
 *                        escapados ({@code %foo%}), o null si no se filtra
 */
public record InmuebleFiltros(
        EstadoInmueble estado,
        TipoInmueble tipo,
        String municipioPatron,
        BigDecimal precioMin,
        BigDecimal precioMax,
        Short habitacionesMin,
        Boolean destacado) {

    /**
     * Normaliza los precios para que el record sea una CLAVE DE CACHÉ estable
     * (issue #27): {@code 100000}, {@code 100000.0} y {@code 100000.00} son el
     * mismo filtro, pero {@code BigDecimal.equals} distingue la escala y
     * duplicaría las entradas de {@code ConteoInmueblesCache}. Se aplica
     * {@code stripTrailingZeros} y se fija {@code scale >= 0} para no mandar
     * notación científica ({@code 1E+5}) al bindeo JDBC. El valor numérico que
     * llega a la query no cambia.
     */
    public InmuebleFiltros {
        precioMin = normalizarPrecio(precioMin);
        precioMax = normalizarPrecio(precioMax);
    }

    private static BigDecimal normalizarPrecio(BigDecimal precio) {
        if (precio == null) {
            return null;
        }
        BigDecimal sinCeros = precio.stripTrailingZeros();
        return sinCeros.scale() < 0 ? sinCeros.setScale(0) : sinCeros;
    }
}
