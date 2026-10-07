package es.manti.swipes.infra.adapter;

import es.manti.swipes.domain.DecisionSwipe;
import es.manti.swipes.domain.InmuebleSwipeResumen;
import es.manti.swipes.domain.Swipe;
import es.manti.swipes.domain.SwipeCursor;
import es.manti.swipes.domain.SwipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador JPA del puerto {@link SwipeRepository}. Ramifica entre la query
 * "sin cursor" (primera página: la B-tree entrega las filas ya ordenadas) y la
 * "con cursor" (keyset con predicado de rango), el mismo patrón — y por las
 * mismas razones medidas con {@code EXPLAIN} — que
 * {@code es.manti.inmuebles.infra.adapter.InmuebleJpaRepository}.
 */
@Repository
@RequiredArgsConstructor
public class SwipeRepositoryAdapter implements SwipeRepository {

    private final SwipeJpaRepository jpaRepository;

    @Override
    public Optional<Swipe> porUsuarioEInmueble(Long usuarioId, Long inmuebleId) {
        return jpaRepository.findByUsuarioIdAndInmuebleId(usuarioId, inmuebleId);
    }

    @Override
    public Swipe guardar(Swipe swipe) {
        return jpaRepository.save(swipe);
    }

    @Override
    public void eliminar(Swipe swipe) {
        jpaRepository.delete(swipe);
    }

    @Override
    public boolean existeInmuebleVigente(Long inmuebleId) {
        return jpaRepository.contarInmuebleVigente(inmuebleId) > 0;
    }

    @Override
    public List<InmuebleSwipeResumen> deck(Long usuarioId, SwipeCursor cursor, int limite) {
        Pageable pagina = PageRequest.of(0, limite);
        return cursor == null
                ? jpaRepository.deck(usuarioId, pagina)
                : jpaRepository.deckConCursor(usuarioId, cursor.creadoEn(), cursor.id(), pagina);
    }

    @Override
    public long contarDeck(Long usuarioId) {
        return jpaRepository.contarDeck(usuarioId);
    }

    @Override
    public List<InmuebleSwipeResumen> favoritos(Long usuarioId, SwipeCursor cursor, int limite) {
        Pageable pagina = PageRequest.of(0, limite);
        return cursor == null
                ? jpaRepository.favoritos(usuarioId, DecisionSwipe.LIKE, pagina)
                : jpaRepository.favoritosConCursor(usuarioId, DecisionSwipe.LIKE,
                        cursor.creadoEn(), cursor.id(), pagina);
    }

    @Override
    public long contarFavoritos(Long usuarioId) {
        return jpaRepository.contarFavoritos(usuarioId, DecisionSwipe.LIKE);
    }
}
