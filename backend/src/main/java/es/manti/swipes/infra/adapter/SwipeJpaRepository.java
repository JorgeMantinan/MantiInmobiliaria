package es.manti.swipes.infra.adapter;

import es.manti.swipes.domain.DecisionSwipe;
import es.manti.swipes.domain.InmuebleSwipeResumen;
import es.manti.swipes.domain.Swipe;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA de {@code swipes}.
 *
 * <p><b>Acoplamiento (decisión documentada en el PR):</b> las queries de
 * deck/favoritos leen la tabla {@code inmuebles} referenciándola por NOMBRE en
 * la string JPQL ({@code FROM Inmueble i}) — Hibernate resuelve la entidad
 * registrada sin que este paquete la importe — y proyectan hacia
 * {@link InmuebleSwipeResumen}, un record del dominio de <b>este</b> módulo.
 * Ni una sola línea importa {@code es.manti.inmuebles.domain.*} ni
 * {@code es.manti.inmuebles.infra.*}: lo comprueba {@code ArquitecturaTest}.
 * Es el precio de ser un monolito de esquema compartido sin romper el aislamiento
 * de módulos; la alternativa (asociación JPA {@code @ManyToOne} a la entidad de
 * inmuebles) habría acoplado el dominio de swipes al de inmuebles.</p>
 *
 * <p>Sin N+1: cada página es UNA query con proyección ligera (17 columnas
 * ligeras, cero colecciones) más, sólo en la primera página, un COUNT. El
 * {@code Pageable} sólo aporta el {@code LIMIT}: el predicado keyset va DENTRO
 * de la query, nunca un OFFSET profundo (skill consultas-eficientes).</p>
 */
public interface SwipeJpaRepository extends JpaRepository<Swipe, Long> {

    /** Proyección de listado. El {@code CAST} deja {@code tipo}/{@code estado} como literal. */
    String RESUMEN = """
            SELECT new es.manti.swipes.domain.InmuebleSwipeResumen(
                i.id, i.titulo, i.precio, CAST(i.tipo AS string), CAST(i.estado AS string),
                i.superficieM2, i.habitaciones, i.banios, i.municipio, i.provincia,
                i.imagenUrl, i.destacado, i.lat, i.lng, i.createdAt,
                i.createdAt, i.id)
            FROM Inmueble i
            WHERE i.eliminadoEn IS NULL
            """;

    /** Anti-join en UNA query: el correlacionado se resuelve por la UNIQUE (usuario_id, inmueble_id). */
    String NO_SWIPEADO = """
            AND NOT EXISTS (
                SELECT s.id FROM Swipe s
                WHERE s.usuarioId = :usuarioId AND s.inmuebleId = i.id
            )
            """;

    /** Misma proyección unida al swipe para conocer su clave de orden (creado_en, id). */
    String FAVORITOS = """
            SELECT new es.manti.swipes.domain.InmuebleSwipeResumen(
                i.id, i.titulo, i.precio, CAST(i.tipo AS string), CAST(i.estado AS string),
                i.superficieM2, i.habitaciones, i.banios, i.municipio, i.provincia,
                i.imagenUrl, i.destacado, i.lat, i.lng, i.createdAt,
                s.creadoEn, s.id)
            FROM Swipe s, Inmueble i
            WHERE s.inmuebleId = i.id
              AND s.usuarioId = :usuarioId
              AND s.decision = :decision
              AND i.eliminadoEn IS NULL
            """;

    // ------------------------------------------------------------------
    // Deck
    // ------------------------------------------------------------------

    @Query(RESUMEN + NO_SWIPEADO + """
            ORDER BY i.createdAt DESC, i.id DESC
            """)
    List<InmuebleSwipeResumen> deck(
            @Param("usuarioId") Long usuarioId,
            Pageable pageable);

    /** Keyset: {@code (created_at, id) < (?, ?)} expresado como rango + desempate. */
    @Query(RESUMEN + NO_SWIPEADO + """
            AND i.createdAt <= :cursorCreadoEn
            AND (i.createdAt < :cursorCreadoEn OR i.id < :cursorId)
            ORDER BY i.createdAt DESC, i.id DESC
            """)
    List<InmuebleSwipeResumen> deckConCursor(
            @Param("usuarioId") Long usuarioId,
            @Param("cursorCreadoEn") Instant cursorCreadoEn,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("""
            SELECT COUNT(i) FROM Inmueble i
            WHERE i.eliminadoEn IS NULL
            """ + NO_SWIPEADO)
    long contarDeck(@Param("usuarioId") Long usuarioId);

    // ------------------------------------------------------------------
    // Favoritos
    // ------------------------------------------------------------------

    @Query(FAVORITOS + """
            ORDER BY s.creadoEn DESC, s.id DESC
            """)
    List<InmuebleSwipeResumen> favoritos(
            @Param("usuarioId") Long usuarioId,
            @Param("decision") DecisionSwipe decision,
            Pageable pageable);

    /** Keyset sobre {@code swipes}: {@code (creado_en, id) < (?, ?)}. */
    @Query(FAVORITOS + """
            AND s.creadoEn <= :cursorCreadoEn
            AND (s.creadoEn < :cursorCreadoEn OR s.id < :cursorId)
            ORDER BY s.creadoEn DESC, s.id DESC
            """)
    List<InmuebleSwipeResumen> favoritosConCursor(
            @Param("usuarioId") Long usuarioId,
            @Param("decision") DecisionSwipe decision,
            @Param("cursorCreadoEn") Instant cursorCreadoEn,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("""
            SELECT COUNT(s) FROM Swipe s, Inmueble i
            WHERE s.inmuebleId = i.id
              AND s.usuarioId = :usuarioId
              AND s.decision = :decision
              AND i.eliminadoEn IS NULL
            """)
    long contarFavoritos(
            @Param("usuarioId") Long usuarioId,
            @Param("decision") DecisionSwipe decision);

    // ------------------------------------------------------------------
    // Escrituras
    // ------------------------------------------------------------------

    Optional<Swipe> findByUsuarioIdAndInmuebleId(Long usuarioId, Long inmuebleId);

    /** Sólo inmuebles vigentes: condición para aceptar un swipe (404 si no). */
    @Query("SELECT COUNT(i) FROM Inmueble i WHERE i.id = :id AND i.eliminadoEn IS NULL")
    long contarInmuebleVigente(@Param("id") Long id);
}
