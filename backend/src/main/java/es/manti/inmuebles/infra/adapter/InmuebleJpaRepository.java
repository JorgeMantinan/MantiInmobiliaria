package es.manti.inmuebles.infra.adapter;

import es.manti.inmuebles.domain.EstadoInmueble;
import es.manti.inmuebles.domain.Inmueble;
import es.manti.inmuebles.domain.InmuebleResumen;
import es.manti.inmuebles.domain.TipoInmueble;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA de {@code inmuebles}. SEIS queries de listado — tres órdenes
 * × (sin cursor | con cursor) — por dos motivos medidos con EXPLAIN ANALYZE:
 *
 * <ol>
 *   <li><b>Sin cursor</b> la query no lleva predicado de keyset: la B-tree
 *       ({@code idx_inmuebles_creado_en_id} / {@code idx_inmuebles_precio_id})
 *       entrega las filas ya ordenadas y el {@code LIMIT} corta en la primera
 *       página (Index Scan Backward, sin Sort).</li>
 *   <li><b>Con cursor</b> el predicado va como <i>rango sobre la columna de
 *       orden</i> ({@code created_at <= ?} + desempate) y NO como
 *       {@code (? IS NULL OR a < ? OR ...)}: con la guarda {@code IS NULL}
 *       PostgreSQL no puede convertir la expresión en <i>Index Cond</i> y cae
 *       en un Filter que recorre el índice entero — medido: 0,08 ms vs 38 ms
 *       con 200 000 filas. Por eso el "sin cursor" es un método aparte.</li>
 * </ol>
 *
 * El predicado es keyset, nunca OFFSET (skill consultas-eficientes); el
 * {@code Pageable} sólo aporta el {@code LIMIT}.
 */
public interface InmuebleJpaRepository extends JpaRepository<Inmueble, Long> {

    /** Filtros comunes; los nulos se eliminan (cada parámetro sólo aparece en su rama). */
    String FILTROS = """
            AND (:estado IS NULL OR i.estado = :estado)
            AND (:tipo IS NULL OR i.tipo = :tipo)
            AND (:municipioPatron IS NULL OR LOWER(i.municipio) LIKE LOWER(:municipioPatron) ESCAPE '!')
            AND (:precioMin IS NULL OR i.precio >= :precioMin)
            AND (:precioMax IS NULL OR i.precio <= :precioMax)
            AND (:habitacionesMin IS NULL OR i.habitaciones >= :habitacionesMin)
            AND (:destacado IS NULL OR i.destacado = :destacado)
            """;

    String RESUMEN = """
            SELECT new es.manti.inmuebles.domain.InmuebleResumen(
                i.id, i.titulo, i.precio, i.tipo, i.estado, i.superficieM2,
                i.habitaciones, i.banios, i.municipio, i.provincia, i.imagenUrl,
                i.destacado, i.lat, i.lng, i.createdAt)
            FROM Inmueble i
            WHERE i.eliminadoEn IS NULL
            """;

    // ------------------------------------------------------------------
    // Orden por defecto: created_at DESC, id DESC
    // ------------------------------------------------------------------

    @Query(RESUMEN + FILTROS + """
            ORDER BY i.createdAt DESC, i.id DESC
            """)
    List<InmuebleResumen> listarPorCreadoDesc(
            @Param("estado") EstadoInmueble estado,
            @Param("tipo") TipoInmueble tipo,
            @Param("municipioPatron") String municipioPatron,
            @Param("precioMin") BigDecimal precioMin,
            @Param("precioMax") BigDecimal precioMax,
            @Param("habitacionesMin") Short habitacionesMin,
            @Param("destacado") Boolean destacado,
            Pageable pageable);

    /** Keyset: {@code (created_at, id) < (?, ?)} expresado como rango + desempate. */
    @Query(RESUMEN + FILTROS + """
            AND i.createdAt <= :cursorCreadoEn
            AND (i.createdAt < :cursorCreadoEn OR i.id < :cursorId)
            ORDER BY i.createdAt DESC, i.id DESC
            """)
    List<InmuebleResumen> listarPorCreadoDescConCursor(
            @Param("estado") EstadoInmueble estado,
            @Param("tipo") TipoInmueble tipo,
            @Param("municipioPatron") String municipioPatron,
            @Param("precioMin") BigDecimal precioMin,
            @Param("precioMax") BigDecimal precioMax,
            @Param("habitacionesMin") Short habitacionesMin,
            @Param("destacado") Boolean destacado,
            @Param("cursorCreadoEn") Instant cursorCreadoEn,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    // ------------------------------------------------------------------
    // sort=precio_asc
    // ------------------------------------------------------------------

    @Query(RESUMEN + FILTROS + """
            ORDER BY i.precio ASC, i.id ASC
            """)
    List<InmuebleResumen> listarPorPrecioAsc(
            @Param("estado") EstadoInmueble estado,
            @Param("tipo") TipoInmueble tipo,
            @Param("municipioPatron") String municipioPatron,
            @Param("precioMin") BigDecimal precioMin,
            @Param("precioMax") BigDecimal precioMax,
            @Param("habitacionesMin") Short habitacionesMin,
            @Param("destacado") Boolean destacado,
            Pageable pageable);

    /** Keyset: {@code (precio, id) > (?, ?)}. */
    @Query(RESUMEN + FILTROS + """
            AND i.precio >= :cursorPrecio
            AND (i.precio > :cursorPrecio OR i.id > :cursorId)
            ORDER BY i.precio ASC, i.id ASC
            """)
    List<InmuebleResumen> listarPorPrecioAscConCursor(
            @Param("estado") EstadoInmueble estado,
            @Param("tipo") TipoInmueble tipo,
            @Param("municipioPatron") String municipioPatron,
            @Param("precioMin") BigDecimal precioMin,
            @Param("precioMax") BigDecimal precioMax,
            @Param("habitacionesMin") Short habitacionesMin,
            @Param("destacado") Boolean destacado,
            @Param("cursorPrecio") BigDecimal cursorPrecio,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    // ------------------------------------------------------------------
    // sort=precio_desc
    // ------------------------------------------------------------------

    @Query(RESUMEN + FILTROS + """
            ORDER BY i.precio DESC, i.id DESC
            """)
    List<InmuebleResumen> listarPorPrecioDesc(
            @Param("estado") EstadoInmueble estado,
            @Param("tipo") TipoInmueble tipo,
            @Param("municipioPatron") String municipioPatron,
            @Param("precioMin") BigDecimal precioMin,
            @Param("precioMax") BigDecimal precioMax,
            @Param("habitacionesMin") Short habitacionesMin,
            @Param("destacado") Boolean destacado,
            Pageable pageable);

    /** Keyset: {@code (precio, id) < (?, ?)}. */
    @Query(RESUMEN + FILTROS + """
            AND i.precio <= :cursorPrecio
            AND (i.precio < :cursorPrecio OR i.id < :cursorId)
            ORDER BY i.precio DESC, i.id DESC
            """)
    List<InmuebleResumen> listarPorPrecioDescConCursor(
            @Param("estado") EstadoInmueble estado,
            @Param("tipo") TipoInmueble tipo,
            @Param("municipioPatron") String municipioPatron,
            @Param("precioMin") BigDecimal precioMin,
            @Param("precioMax") BigDecimal precioMax,
            @Param("habitacionesMin") Short habitacionesMin,
            @Param("destacado") Boolean destacado,
            @Param("cursorPrecio") BigDecimal cursorPrecio,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    // ------------------------------------------------------------------

    @Query("""
            SELECT COUNT(i)
            FROM Inmueble i
            WHERE i.eliminadoEn IS NULL
            """ + FILTROS)
    long contar(
            @Param("estado") EstadoInmueble estado,
            @Param("tipo") TipoInmueble tipo,
            @Param("municipioPatron") String municipioPatron,
            @Param("precioMin") BigDecimal precioMin,
            @Param("precioMax") BigDecimal precioMax,
            @Param("habitacionesMin") Short habitacionesMin,
            @Param("destacado") Boolean destacado);

    /** Detalle público: sólo anuncios vigentes. */
    Optional<Inmueble> findByIdAndEliminadoEnIsNull(Long id);
}
