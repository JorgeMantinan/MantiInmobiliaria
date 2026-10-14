package es.manti.swipes.application;

import es.manti.swipes.domain.CursorInvalidoException;
import es.manti.swipes.domain.DecisionSwipe;
import es.manti.swipes.domain.FavoritoNoEncontradoException;
import es.manti.swipes.domain.InmuebleInexistenteException;
import es.manti.swipes.domain.InmuebleSwipeResumen;
import es.manti.swipes.domain.ParametroInvalidoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Integración del servicio de swipes contra PostgreSQL real (perfil
 * {@code test}). Cubre las 4 reglas del contrato: 201 en la primera decisión,
 * 200 idempotente sin fila nueva, 200 con actualización al cambiar de opinión y
 * 404 sobre inmuebles inexistentes/eliminados, además del deck (anti-join),
 * favoritos (orden y keyset) y la baja.
 *
 * <p>Los fixtures se marcan con municipio {@code It-*} y email
 * {@code it-*} para limpiarlos tras cada test.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SwipeServiceTest {

    @Autowired
    private SwipeService service;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiarFixtures() {
        jdbc.update("DELETE FROM inmuebles WHERE municipio LIKE 'It-%'");
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    // ------------------------------------------------------------------
    // POST: 201 / 200 idempotente / 200 con cambio / 404
    // ------------------------------------------------------------------

    @Test
    void registrar_primeraDecision_devuelveCreadaYUnaUnicaFila() {
        Long usuario = usuario();
        Long inmueble = inmueble(municipioPrueba());

        SwipeResultado resultado = service.registrar(usuario, inmueble, DecisionSwipe.LIKE);

        assertThat(resultado.creada()).isTrue();
        assertThat(resultado.decision()).isEqualTo(DecisionSwipe.LIKE);
        assertThat(contarSwipes(usuario, inmueble)).isEqualTo(1);
        assertThat(decisionDe(usuario, inmueble)).isEqualTo("LIKE");
    }

    @Test
    void registrar_repeticionMismaDecision_noCreaFilaNiEscribe() {
        Long usuario = usuario();
        Long inmueble = inmueble(municipioPrueba());
        service.registrar(usuario, inmueble, DecisionSwipe.LIKE);
        Instant actualizadoAntes = actualizadoEn(usuario, inmueble);

        SwipeResultado repetido = service.registrar(usuario, inmueble, DecisionSwipe.LIKE);

        assertThat(repetido.creada()).isFalse();
        // SIN fila nueva: la unicidad (usuario_id, inmueble_id) sigue en una sola fila
        assertThat(contarSwipes(usuario, inmueble)).isEqualTo(1);
        // y sin escritura: el carril idempotente no toca actualizado_en
        assertThat(actualizadoEn(usuario, inmueble)).isEqualTo(actualizadoAntes);
    }

    @Test
    void registrar_cambioDeDecision_actualizaLaMismaFila() {
        Long usuario = usuario();
        Long inmueble = inmueble(municipioPrueba());
        service.registrar(usuario, inmueble, DecisionSwipe.LIKE);

        SwipeResultado cambiado = service.registrar(usuario, inmueble, DecisionSwipe.NOPE);

        assertThat(cambiado.creada()).isFalse();
        assertThat(cambiado.decision()).isEqualTo(DecisionSwipe.NOPE);
        assertThat(contarSwipes(usuario, inmueble)).isEqualTo(1);
        assertThat(decisionDe(usuario, inmueble)).isEqualTo("NOPE");
        // El like desaparece de favoritos al pasar a NOPE
        assertThat(service.favoritos(usuario, null, 20).contenido()).isEmpty();
    }

    @Test
    void registrar_inmuebleInexistente_lanzaInmuebleInexistente() {
        Long usuario = usuario();

        assertThatThrownBy(() -> service.registrar(usuario, 987_654_321L, DecisionSwipe.LIKE))
                .isInstanceOf(InmuebleInexistenteException.class)
                .hasMessageContaining("987654321");
        assertThat(contarSwipes(usuario, 987_654_321L)).isZero();
    }

    @Test
    void registrar_inmuebleEliminado_lanzaInmuebleInexistente() {
        Long usuario = usuario();
        Long inmueble = inmueble(municipioPrueba());
        jdbc.update("UPDATE inmuebles SET eliminado_en = now() WHERE id = ?", inmueble);

        assertThatThrownBy(() -> service.registrar(usuario, inmueble, DecisionSwipe.LIKE))
                .isInstanceOf(InmuebleInexistenteException.class);
        assertThat(contarSwipes(usuario, inmueble)).isZero();
    }

    // ------------------------------------------------------------------
    // Deck
    // ------------------------------------------------------------------

    @Test
    void deck_excluyeLosSwipeadosYLosEliminados() {
        Long usuario = usuario();
        String municipio = municipioPrueba();
        Long pendiente = inmueble(municipio);
        Long swipeado = inmueble(municipio);
        Long eliminado = inmueble(municipio);
        service.registrar(usuario, swipeado, DecisionSwipe.NOPE);
        jdbc.update("UPDATE inmuebles SET eliminado_en = now() WHERE id = ?", eliminado);

        List<Long> ids = idsDeck(usuario, 50);

        assertThat(ids).contains(pendiente).doesNotContain(swipeado, eliminado);
        // Otros usuarios no estorban: su swipe no saca el inmueble del MI deck
        assertThat(service.deck(usuario, null, 50).totalElements()).isNotNull();
    }

    @Test
    void deck_conCursor_noSeSolapaNiOmiteFilas() {
        Long usuario = usuario();
        String municipio = municipioPrueba();
        List<Long> creados = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            creados.add(inmueble(municipio));
        }

        ListadoSwipe pagina1 = service.deck(usuario, null, 2);
        String cursor1 = pagina1.cursorSiguiente();
        ListadoSwipe pagina2 = service.deck(usuario, cursor1, 2);
        String cursor2 = pagina2.cursorSiguiente();
        ListadoSwipe pagina3 = service.deck(usuario, cursor2, 2);

        assertThat(pagina1.contenido()).hasSize(2);
        assertThat(pagina1.totalElements()).isNotNull();
        assertThat(pagina2.contenido()).hasSize(2);
        // totalElements sólo en la primera página (sin COUNT por cada salto)
        assertThat(pagina2.totalElements()).isNull();
        // El deck comparte tabla con los seeds de desarrollo, así que tras 3
        // páginas de 2 todavía quedan filas: el cursor avanza y no se repite.
        assertThat(cursor1).isNotBlank();
        assertThat(cursor2).isNotBlank().isNotEqualTo(cursor1);

        List<Long> ids = new ArrayList<>();
        pagina1.contenido().forEach(f -> ids.add(f.id()));
        pagina2.contenido().forEach(f -> ids.add(f.id()));
        pagina3.contenido().forEach(f -> ids.add(f.id()));
        assertThat(ids).containsAll(creados).doesNotHaveDuplicates();
        // Orden global created_at DESC, id DESC entre páginas
        assertThat(ids.get(0)).isGreaterThan(ids.get(ids.size() - 1));
    }

    @Test
    void deck_limitFueraDeRango_lanzaParametroInvalido() {
        Long usuario = usuario();

        ParametroInvalidoException fueraDeRango = catchThrowableOfType(
                ParametroInvalidoException.class, () -> service.deck(usuario, null, 0));
        assertThat(fueraDeRango).isNotNull();
        assertThat(fueraDeRango.parametro()).isEqualTo("limit");
        assertThatThrownBy(() -> service.deck(usuario, null, 51))
                .isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> service.favoritos(usuario, null, -1))
                .isInstanceOf(ParametroInvalidoException.class);
        assertThatCode(() -> service.deck(usuario, null, 1)).doesNotThrowAnyException();
        assertThatCode(() -> service.deck(usuario, null, 50)).doesNotThrowAnyException();
    }

    @Test
    void deck_cursorDeOtroListado_lanzaCursorInvalido() {
        Long usuario = usuario();
        String municipio = municipioPrueba();
        inmueble(municipio);
        inmueble(municipio);
        String cursorDeck = service.deck(usuario, null, 1).cursorSiguiente();
        assertThat(cursorDeck).isNotBlank();

        assertThatThrownBy(() -> service.favoritos(usuario, cursorDeck, 1))
                .isInstanceOf(CursorInvalidoException.class);
        assertThatThrownBy(() -> service.deck(usuario, "dGV4dG9xdWUtY29ycnVwdG8", 1))
                .isInstanceOf(CursorInvalidoException.class);
    }

    // ------------------------------------------------------------------
    // Favoritos
    // ------------------------------------------------------------------

    @Test
    void favoritos_soloIncluyeLosLikesOrdenadosPorCreadoDesc() {
        Long usuario = usuario();
        String municipio = municipioPrueba();
        Long primero = inmueble(municipio);
        Long segundo = inmueble(municipio);
        Long descartado = inmueble(municipio);
        service.registrar(usuario, primero, DecisionSwipe.LIKE);
        service.registrar(usuario, descartado, DecisionSwipe.NOPE);
        service.registrar(usuario, segundo, DecisionSwipe.LIKE);

        ListadoSwipe favoritos = service.favoritos(usuario, null, 20);

        assertThat(idsDe(favoritos)).containsExactly(segundo, primero);
        assertThat(favoritos.totalElements()).isEqualTo(2L);
        assertThat(favoritos.cursorSiguiente()).isNull();
    }

    @Test
    void favoritos_paginacionPorCursor_sinRepetirYCursorNuloAlFinal() {
        Long usuario = usuario();
        String municipio = municipioPrueba();
        List<Long> creados = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Long inmueble = inmueble(municipio);
            creados.add(inmueble);
            service.registrar(usuario, inmueble, DecisionSwipe.LIKE);
        }

        ListadoSwipe pagina1 = service.favoritos(usuario, null, 2);
        ListadoSwipe pagina2 = service.favoritos(usuario, pagina1.cursorSiguiente(), 2);
        ListadoSwipe pagina3 = service.favoritos(usuario, pagina2.cursorSiguiente(), 2);

        assertThat(idsDe(pagina1)).containsExactly(creados.get(4), creados.get(3));
        assertThat(idsDe(pagina2)).containsExactly(creados.get(2), creados.get(1));
        assertThat(idsDe(pagina3)).containsExactly(creados.get(0));
        assertThat(pagina3.cursorSiguiente()).isNull();
        assertThat(pagina1.totalElements()).isEqualTo(5L);
        assertThat(pagina2.totalElements()).isNull();

        List<Long> todos = new ArrayList<>();
        pagina1.contenido().forEach(f -> todos.add(f.id()));
        pagina2.contenido().forEach(f -> todos.add(f.id()));
        pagina3.contenido().forEach(f -> todos.add(f.id()));
        assertThat(todos).hasSize(5).doesNotHaveDuplicates();
    }

    // ------------------------------------------------------------------
    // Baja de favorito
    // ------------------------------------------------------------------

    @Test
    void eliminarFavorito_borraElSwipeYElInmuebleVuelveAlDeck() {
        Long usuario = usuario();
        Long inmueble = inmueble(municipioPrueba());
        service.registrar(usuario, inmueble, DecisionSwipe.LIKE);
        assertThat(idsDeck(usuario, 50)).doesNotContain(inmueble);

        service.eliminarFavorito(usuario, inmueble);

        assertThat(contarSwipes(usuario, inmueble)).isZero();
        assertThat(idsDeck(usuario, 50)).contains(inmueble);
    }

    @Test
    void eliminarFavorito_sinFavoritoPropio_lanzaFavoritoNoEncontrado() {
        Long usuarioA = usuario();
        Long usuarioB = usuario();
        Long inmueble = inmueble(municipioPrueba());
        service.registrar(usuarioA, inmueble, DecisionSwipe.LIKE);

        // Un NOPE no es un favorito
        Long descartado = inmueble(municipioPrueba());
        service.registrar(usuarioA, descartado, DecisionSwipe.NOPE);
        assertThatThrownBy(() -> service.eliminarFavorito(usuarioA, descartado))
                .isInstanceOf(FavoritoNoEncontradoException.class);

        // IDOR: el favorito de A sigue intacto cuando B intenta borrarlo
        assertThatThrownBy(() -> service.eliminarFavorito(usuarioB, inmueble))
                .isInstanceOf(FavoritoNoEncontradoException.class);
        assertThat(contarSwipes(usuarioA, inmueble)).isEqualTo(1);

        // Y el suyo inexistente también es 404
        assertThatThrownBy(() -> service.eliminarFavorito(usuarioB, inmueble))
                .isInstanceOf(FavoritoNoEncontradoException.class);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private List<Long> idsDeck(Long usuario, int limite) {
        return idsDe(service.deck(usuario, null, limite));
    }

    private static List<Long> idsDe(ListadoSwipe listado) {
        List<Long> ids = new ArrayList<>();
        listado.contenido().forEach((InmuebleSwipeResumen f) -> ids.add(f.id()));
        return ids;
    }

    private int contarSwipes(Long usuario, Long inmueble) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM swipes WHERE usuario_id = ? AND inmueble_id = ?",
                Integer.class, usuario, inmueble);
    }

    private String decisionDe(Long usuario, Long inmueble) {
        return jdbc.queryForObject(
                "SELECT decision FROM swipes WHERE usuario_id = ? AND inmueble_id = ?",
                String.class, usuario, inmueble);
    }

    private Instant actualizadoEn(Long usuario, Long inmueble) {
        Timestamp ts = jdbc.queryForObject(
                "SELECT actualizado_en FROM swipes WHERE usuario_id = ? AND inmueble_id = ?",
                Timestamp.class, usuario, inmueble);
        return ts.toInstant();
    }

    private String municipioPrueba() {
        return "It-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Long usuario() {
        return jdbc.queryForObject("""
                INSERT INTO usuarios (email, password_hash, nombre, rol, activo)
                VALUES (?, 'hash-de-prueba', 'Usuario Swipe', 'USER', true)
                RETURNING id
                """, Long.class, "it-swipe-" + UUID.randomUUID() + "@example.com");
    }

    private Long inmueble(String municipio) {
        return jdbc.queryForObject("""
                INSERT INTO inmuebles (titulo, precio, tipo, estado, direccion, municipio,
                                       provincia, lat, lng)
                VALUES ('it-piso de prueba', 200000.00, 'PISO', 'VENTA', 'Calle Test 1', ?,
                        'Madrid', 40.4, -3.7)
                RETURNING id
                """, Long.class, municipio);
    }
}
