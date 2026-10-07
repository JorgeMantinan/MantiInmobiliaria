package es.manti.swipes.domain;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia de swipes. Implementado en infra por Spring Data JPA
 * (patrón idéntico al de inmuebles y usuarios).
 *
 * <p><b>Monolito de esquema compartido:</b> las lecturas de deck/favoritos
 * leen la tabla {@code inmuebles} (proyección {@link InmuebleSwipeResumen})
 * SIN importar su entidad ni su repositorio — se referencian por nombre en las
 * strings JPQL del adaptador. Es la única alternativa sin acoplar
 * {@code swipes} a {@code es.manti.inmuebles.domain/infra}, prohibido por
 * ArchUnit. La decisión queda documentada en el PR.</p>
 */
public interface SwipeRepository {

    /**
     * Swipe del usuario sobre ese inmueble, con cualquier decisión. Se usa para
     * decidir entre insertar (201), no tocar (200 idempotente) o actualizar (200).
     */
    Optional<Swipe> porUsuarioEInmueble(Long usuarioId, Long inmuebleId);

    Swipe guardar(Swipe swipe);

    void eliminar(Swipe swipe);

    /**
     * ¿Existe el inmueble y no está en soft-delete? Condición para aceptar un
     * swipe (404 si no). Consulta puntual sobre la PK de {@code inmuebles}
     * (Index Scan, una fila).
     */
    boolean existeInmuebleVigente(Long inmuebleId);

    /**
     * Página keyset del deck: inmuebles ACTIVOS que el usuario NO ha swipeado.
     *
     * <p><b>Plan esperado</b> (una sola query, sin bucle ni N+1): el planner de
     * PostgreSQL convierte el {@code NOT EXISTS} en <i>anti-join</i> sondeando
     * {@code uq_swipes_usuario_inmueble (usuario_id, inmueble_id)} — índice
     * único que cubre exactamente las dos columnas del correlacionado — mientras
     * el lado exterior recorre {@code idx_inmuebles_creado_en_id} (parcial con
     * {@code eliminado_en IS NULL}) en orden inverso: <i>Index Scan Backward</i>
     * + {@code LIMIT}, <b>sin Sort</b> y sin materializar swipes.</p>
     *
     * @param cursor última fila de la página anterior; null en la primera página
     * @param limite máximo de filas (el servicio pide {@code limite + 1})
     */
    List<InmuebleSwipeResumen> deck(Long usuarioId, SwipeCursor cursor, int limite);

    /** Total de inmuebles pendientes del deck, para {@code totalElements} (sólo primera página). */
    long contarDeck(Long usuarioId);

    /**
     * Página keyset de favoritos: swipes {@code LIKE} del usuario unidos a su
     * inmueble vigente, ordenados por {@code creado_en DESC, id DESC}.
     *
     * <p><b>Plan esperado:</b> sondeo por igualdad sobre
     * {@code idx_swipes_usuario_creado (usuario_id, creado_en)} (rango de
     * {@code creado_en} desde el cursor + {@code LIMIT}), sondeo a {@code
     * inmuebles} por su PK y filtro de {@code eliminado_en IS NULL}. Las
     * igualdades de {@code decision} se aplican después del rango: sólo se
     * materializan las filas de la página. Ante empates exactos de
     * {@code creado_en} el desempate por {@code id} puede obligar a ordenar el
     * grupo empatado (sort local sobre el rango, no sobre la tabla entera).</p>
     *
     * @param cursor última fila de la página anterior; null en la primera página
     * @param limite máximo de filas (el servicio pide {@code limite + 1})
     */
    List<InmuebleSwipeResumen> favoritos(Long usuarioId, SwipeCursor cursor, int limite);

    /** Total de favoritos vigentes del usuario, para {@code totalElements} (sólo primera página). */
    long contarFavoritos(Long usuarioId);
}
