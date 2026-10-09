package es.manti.swipes.application;

import es.manti.swipes.domain.CursorInvalidoException;
import es.manti.swipes.domain.DecisionSwipe;
import es.manti.swipes.domain.FavoritoNoEncontradoException;
import es.manti.swipes.domain.InmuebleInexistenteException;
import es.manti.swipes.domain.InmuebleSwipeResumen;
import es.manti.swipes.domain.ParametroInvalidoException;
import es.manti.swipes.domain.Swipe;
import es.manti.swipes.domain.SwipeCursor;
import es.manti.swipes.domain.SwipeCursor.Ambito;
import es.manti.swipes.domain.SwipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Casos de uso del módulo swipes: decidir sobre un inmueble (201/200/404),
 * el deck de pendientes, el listado de favoritos y la baja de un favorito.
 *
 * <p>TODA escritura va en transacción de escritura ({@link Transactional} o
 * {@link TransactionTemplate}) y toda lectura en {@code readOnly = true}: el
 * deck y los favoritos son feeds y no deben abrir transacciones de escritura
 * ni bloquear nada.</p>
 *
 * <p>Idempotencia del POST por DISEÑO, sin cabecera {@code Idempotency-Key}:
 * repetir la MISMA decisión no escribe una fila nueva ni toca
 * {@code actualizado_en}; cambiar de opinión actualiza la fila que ya existe;
 * la unicidad la garantiza {@code uq_swipes_usuario_inmueble}. Ver
 * {@link #registrar} para la carrera de inserción (retry único).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SwipeService {

    /** Tamaño máximo de página (contract fijo con el frontend: 20 por defecto, 1..50). */
    static final int LIMITE_MAXIMO = 50;

    private final SwipeRepository repository;

    /**
     * Plantilla para los dos intentos de {@link #registrar}: cada uno corre en
     * SU propia transacción (posturas {@code PROPAGATION_REQUIRED} por defecto).
     * Hace falta porque PostgreSQL aborta la transacción en la que salta la
     * violación de la UNIQUE, así que una relectura dentro de esa misma
     * transacción fallaría con «current transaction is aborted» en vez de
     * devolver la fila ganadora.
     */
    private final TransactionTemplate transaccion;

    /**
     * Registra la decisión del usuario sobre un inmueble.
     *
     * <p><b>Idempotente por diseño</b> ({@code uq_swipes_usuario_inmueble}),
     * no requiere {@code Idempotency-Key}: 201 en la primera decisión y 200 en
     * cualquier repetición. Si dos peticiones simultáneas pasan la comprobación
     * vacía y chocan al insertar (doble tap optimista del frontend), se captura
     * {@link DataIntegrityViolationException}, se relee en una transacción NUEVA
     * y se aplica la misma lógica de repetición/cambio → 200. Es un retry
     * ÚNICO: si la relectura sigue vacía, el choque se relanza y cae en 409
     * (red de seguridad documentada en el endpoint).</p>
     *
     * @return 201 si es la primera vez ({@code creada = true}); 200 si ya había
     *         un swipe (idempotente si la decisión es la misma, actualización
     *         si cambió)
     * @throws InmuebleInexistenteException 404 si el inmueble no existe o está eliminado
     * @throws DataIntegrityViolationException 409 si el reintento no resuelve la carrera
     */
    public SwipeResultado registrar(Long usuarioId, Long inmuebleId, DecisionSwipe decision) {
        try {
            return transaccion.execute(estado -> registrarSinCarrera(usuarioId, inmuebleId, decision));
        } catch (DataIntegrityViolationException choque) {
            return transaccion.execute(estado ->
                    resolverCarreraInsercion(usuarioId, inmuebleId, decision, choque));
        }
    }

    /** Primer intento: comprueba el inmueble, lee la pareja y — si no existe — inserta. */
    private SwipeResultado registrarSinCarrera(Long usuarioId, Long inmuebleId, DecisionSwipe decision) {
        if (!repository.existeInmuebleVigente(inmuebleId)) {
            throw new InmuebleInexistenteException(inmuebleId);
        }

        var existente = repository.porUsuarioEInmueble(usuarioId, inmuebleId);
        if (existente.isEmpty()) {
            repository.guardar(Swipe.nuevo(usuarioId, inmuebleId, decision));
            log.debug("Swipe creado: usuario {} -> inmueble {} ({})", usuarioId, inmuebleId, decision);
            return new SwipeResultado(inmuebleId, decision, true);
        }
        return aplicarDecision(existente.get(), decision);
    }

    /**
     * Retry único tras chocar con la UNIQUE: relectura en transacción nueva y
     * la MISMA lógica de repetición/cambio → 200. Si la fila sigue sin estar,
     * el choque no era el de la pareja usuario+inmueble y se relanza (409).
     */
    private SwipeResultado resolverCarreraInsercion(Long usuarioId, Long inmuebleId,
                                                    DecisionSwipe decision,
                                                    DataIntegrityViolationException choque) {
        var existente = repository.porUsuarioEInmueble(usuarioId, inmuebleId);
        if (existente.isEmpty()) {
            throw choque;
        }
        log.debug("Carrera de inserción resuelta con relectura: usuario {} -> inmueble {}",
                usuarioId, inmuebleId);
        return aplicarDecision(existente.get(), decision);
    }

    /**
     * Carril de los 200: MISMA decisión → cero escrituras; decisión distinta →
     * actualiza la fila existente. Devuelven siempre {@code creada = false}.
     */
    private SwipeResultado aplicarDecision(Swipe swipe, DecisionSwipe decision) {
        if (swipe.getDecision() == decision) {
            return new SwipeResultado(swipe.getInmuebleId(), decision, false);
        }
        swipe.cambiarDecision(decision);
        repository.guardar(swipe);
        log.debug("Swipe actualizado: usuario {} -> inmueble {} ({})",
                swipe.getUsuarioId(), swipe.getInmuebleId(), decision);
        return new SwipeResultado(swipe.getInmuebleId(), decision, false);
    }

    /**
     * Página del deck: inmuebles activos que este usuario todavía no ha
     * swipeado. Orden {@code created_at DESC, id DESC}; cursor opaco base64.
     *
     * @param limite 1..50 (por defecto llega 20); fuera de rango → 400
     * @throws CursorInvalidoException 400 si el cursor está corrupto o es de otro listado
     * @throws ParametroInvalidoException 400 si {@code limit} está fuera de rango
     */
    @Transactional(readOnly = true)
    public ListadoSwipe deck(Long usuarioId, String cursor, int limite) {
        return paginar(cursor, limite, Ambito.DECK,
                c -> repository.deck(usuarioId, c, limite + 1),
                () -> repository.contarDeck(usuarioId));
    }

    /**
     * Página de favoritos (swipes {@code LIKE}) del usuario. Orden
     * {@code creado_en DESC, id DESC} sobre la propia tabla de swipes.
     *
     * @param limite 1..50 (por defecto llega 20); fuera de rango → 400
     * @throws CursorInvalidoException 400 si el cursor está corrupto o es de otro listado
     * @throws ParametroInvalidoException 400 si {@code limit} está fuera de rango
     */
    @Transactional(readOnly = true)
    public ListadoSwipe favoritos(Long usuarioId, String cursor, int limite) {
        return paginar(cursor, limite, Ambito.FAVORITOS,
                c -> repository.favoritos(usuarioId, c, limite + 1),
                () -> repository.contarFavoritos(usuarioId));
    }

    /**
     * Baja de un favorito: borra el swipe y el inmueble vuelve al deck.
     *
     * @throws FavoritoNoEncontradoException 404 si el USUARIO ACTUAL no tiene
     *         ese favorito (también si era un {@code NOPE} o si es favorito de
     *         otro usuario: la query nunca sale de su propio {@code usuario_id})
     */
    @Transactional
    public void eliminarFavorito(Long usuarioId, Long inmuebleId) {
        Swipe favorito = repository.porUsuarioEInmueble(usuarioId, inmuebleId)
                .filter(swipe -> swipe.getDecision().esFavorito())
                .orElseThrow(() -> new FavoritoNoEncontradoException(inmuebleId));
        repository.eliminar(favorito);
        log.debug("Favorito eliminado: usuario {} -> inmueble {}", usuarioId, inmuebleId);
    }

    // ------------------------------------------------------------------
    // Paginación keyset (compartida por deck y favoritos)
    // ------------------------------------------------------------------

    /**
     * Página keyset genérica: valida el límite, decodifica el cursor del ámbito
     * correcto, pide {@code limite + 1} filas para saber si hay página
     * siguiente SIN contar, y sólo emite {@code totalElements} en la primera
     * página (donde sí compensa el COUNT).
     *
     * @param consulta ejecuta la query del listado con el cursor decodificado
     *                 (null en la primera página); el servicio añade el +1
     * @param conteo   total del listado, sólo invocado sin cursor
     */
    private ListadoSwipe paginar(String cursor, int limite, Ambito ambito,
                                 Function<SwipeCursor, List<InmuebleSwipeResumen>> consulta,
                                 Supplier<Long> conteo) {
        validarLimite(limite);
        SwipeCursor ultima = cursor == null || cursor.isBlank()
                ? null
                : SwipeCursor.decodificar(cursor, ambito);

        // Se pide una fila extra para saber si queda página siguiente sin COUNT.
        List<InmuebleSwipeResumen> candidatas = consulta.apply(ultima);
        boolean hayMasPaginas = candidatas.size() > limite;
        List<InmuebleSwipeResumen> contenido = hayMasPaginas
                ? List.copyOf(candidatas.subList(0, limite))
                : candidatas;
        String cursorSiguiente = hayMasPaginas
                ? new SwipeCursor(ambito, contenido.getLast().cursorCreadoEn(),
                        contenido.getLast().cursorId()).codificar()
                : null;
        Long totalElements = ultima == null ? conteo.get() : null;
        log.debug("Página de {}: {} filas, hayMas={}", ambito, contenido.size(), hayMasPaginas);
        return new ListadoSwipe(contenido, cursorSiguiente, totalElements);
    }

    /**
     * Rango de {@code limit} fuera de 1..50 → 400 con
     * {@code errors:[{field: "limit", ...}]}.
     *
     * <p>Se valida en el caso de uso y no con {@code @Min/@Max} porque sólo
     * aquí se puede devolver el nombre del campo en el ProblemDetail (mismo
     * criterio que los rangos de precio de inmuebles).</p>
     */
    static void validarLimite(int limite) {
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new ParametroInvalidoException("limit",
                    "debe estar entre 1 y " + LIMITE_MAXIMO);
        }
    }
}
