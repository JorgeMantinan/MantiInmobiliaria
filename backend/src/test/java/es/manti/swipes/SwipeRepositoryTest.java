package es.manti.swipes;

import es.manti.swipes.domain.DecisionSwipe;
import es.manti.swipes.domain.InmuebleSwipeResumen;
import es.manti.swipes.domain.Swipe;
import es.manti.swipes.domain.SwipeCursor;
import es.manti.swipes.domain.SwipeCursor.Ambito;
import es.manti.swipes.domain.SwipeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integración del repositorio de swipes contra PostgreSQL real: el anti-join
 * del deck (UNA query, sin bucle), el keyset de ambos listados y el union
 * {@code swipes ↔ inmuebles} de favoritos.
 *
 * <p>Los fixtures se marcan con municipio {@code It-*} y email {@code it-*}
 * para limpiarlos tras cada test.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SwipeRepositoryTest {

    @Autowired
    private SwipeRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiarFixtures() {
        jdbc.update("DELETE FROM inmuebles WHERE municipio LIKE 'It-%'");
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    @Test
    void deck_excluyeLoQueElUsuarioYaHaSwipeadoPeroNoLoDeOtros() {
        Long usuario = usuario();
        Long otroUsuario = usuario();
        String municipio = municipioPrueba();
        Long pendiente = inmueble(municipio);
        Long mio = inmueble(municipio);
        Long deOtro = inmueble(municipio);
        repository.guardar(Swipe.nuevo(usuario, mio, DecisionSwipe.NOPE));
        repository.guardar(Swipe.nuevo(otroUsuario, deOtro, DecisionSwipe.LIKE));

        List<Long> ids = idsDeck(usuario, 50);

        assertThat(ids).contains(pendiente, deOtro).doesNotContain(mio);
    }

    @Test
    void deck_excluyeLosInmueblesEliminados() {
        Long usuario = usuario();
        String municipio = municipioPrueba();
        Long vigente = inmueble(municipio);
        Long borrado = inmueble(municipio);
        jdbc.update("UPDATE inmuebles SET eliminado_en = now() WHERE id = ?", borrado);

        List<Long> ids = idsDeck(usuario, 50);

        assertThat(ids).contains(vigente).doesNotContain(borrado);
        assertThat(repository.contarDeck(usuario)).isGreaterThan(0);
    }

    @Test
    void deck_conCursor_keysetNoSeSolapaNiOmiteFilas() {
        Long usuario = usuario();
        String municipio = municipioPrueba();
        List<Long> creados = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            creados.add(inmueble(municipio));
        }

        List<InmuebleSwipeResumen> pagina1 = repository.deck(usuario, null, 3);
        InmuebleSwipeResumen ultima1 = pagina1.get(pagina1.size() - 1);
        List<InmuebleSwipeResumen> pagina2 = repository.deck(usuario,
                new SwipeCursor(Ambito.DECK, ultima1.cursorCreadoEn(), ultima1.cursorId()), 3);

        assertThat(pagina1).hasSize(3);
        assertThat(pagina2).isNotEmpty();
        List<Long> ids = new ArrayList<>();
        pagina1.forEach(f -> ids.add(f.id()));
        pagina2.forEach(f -> ids.add(f.id()));
        assertThat(ids).containsAll(creados).doesNotHaveDuplicates();
        // La clave del cursor del deck es el created_at del INMUEBLE
        assertThat(ultima1.cursorCreadoEn()).isEqualTo(ultima1.createdAt());
        assertThat(ultima1.cursorId()).isEqualTo(ultima1.id());
    }

    @Test
    void deck_proyectaLosMismosCamposQueElResumenPublico() {
        Long usuario = usuario();
        Long inmueble = inmueble(municipioPrueba());

        InmuebleSwipeResumen fila = repository.deck(usuario, null, 50).stream()
                .filter(f -> f.id().equals(inmueble))
                .findFirst()
                .orElseThrow();

        assertThat(fila.titulo()).isEqualTo("it-piso de prueba");
        assertThat(fila.precio()).isNotNull();
        assertThat(fila.tipo()).isEqualTo("PISO");
        assertThat(fila.estado()).isEqualTo("VENTA");
        assertThat(fila.municipio()).isNotBlank();
        assertThat(fila.lat()).isCloseTo(40.4, within(0.0001));
        assertThat(fila.createdAt()).isNotNull();
    }

    @Test
    void favoritos_devuelveSoloLikesOrdenadosPorCreadoDescConSuClaveDeCursor() {
        Long usuario = usuario();
        String municipio = municipioPrueba();
        Long primero = inmueble(municipio);
        Long descartado = inmueble(municipio);
        Long segundo = inmueble(municipio);
        repository.guardar(Swipe.nuevo(usuario, primero, DecisionSwipe.LIKE));
        repository.guardar(Swipe.nuevo(usuario, descartado, DecisionSwipe.NOPE));
        repository.guardar(Swipe.nuevo(usuario, segundo, DecisionSwipe.LIKE));

        List<InmuebleSwipeResumen> filas = repository.favoritos(usuario, null, 20);

        assertThat(filas).extracting(InmuebleSwipeResumen::id).containsExactly(segundo, primero);
        // La clave del cursor de favoritos es creado_en/id del SWIPE
        assertThat(filas.get(0).cursorCreadoEn()).isNotNull();
        assertThat(filas.get(0).cursorId()).isNotNull();
        // y createdAt sigue siendo el del inmueble (campo del resumen)
        assertThat(filas.get(0).createdAt()).isNotNull();
        assertThat(repository.contarFavoritos(usuario)).isEqualTo(2);
    }

    @Test
    void favoritos_conCursor_keysetNoSeSolapaNiOmiteFilas() {
        Long usuario = usuario();
        String municipio = municipioPrueba();
        List<Long> creados = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Long inmueble = inmueble(municipio);
            creados.add(inmueble);
            repository.guardar(Swipe.nuevo(usuario, inmueble, DecisionSwipe.LIKE));
        }

        List<InmuebleSwipeResumen> pagina1 = repository.favoritos(usuario, null, 2);
        InmuebleSwipeResumen ultima1 = pagina1.get(pagina1.size() - 1);
        List<InmuebleSwipeResumen> pagina2 = repository.favoritos(usuario,
                new SwipeCursor(Ambito.FAVORITOS, ultima1.cursorCreadoEn(), ultima1.cursorId()), 10);

        assertThat(pagina1).extracting(InmuebleSwipeResumen::id)
                .containsExactly(creados.get(4), creados.get(3));
        assertThat(pagina2).extracting(InmuebleSwipeResumen::id)
                .containsExactly(creados.get(2), creados.get(1), creados.get(0));
    }

    @Test
    void favoritos_excluyeLosInmueblesEliminados() {
        Long usuario = usuario();
        String municipio = municipioPrueba();
        Long borrado = inmueble(municipio);
        repository.guardar(Swipe.nuevo(usuario, borrado, DecisionSwipe.LIKE));
        jdbc.update("UPDATE inmuebles SET eliminado_en = now() WHERE id = ?", borrado);

        assertThat(repository.favoritos(usuario, null, 20)).isEmpty();
        assertThat(repository.contarFavoritos(usuario)).isZero();
    }

    @Test
    void existeInmuebleVigente_esFalsoParaInexistentesYParaEliminados() {
        Long inmueble = inmueble(municipioPrueba());

        assertThat(repository.existeInmuebleVigente(inmueble)).isTrue();
        assertThat(repository.existeInmuebleVigente(987_654_321L)).isFalse();

        jdbc.update("UPDATE inmuebles SET eliminado_en = now() WHERE id = ?", inmueble);
        assertThat(repository.existeInmuebleVigente(inmueble)).isFalse();
    }

    @Test
    void guardarBuscarYEliminar_swipe() {
        Long usuario = usuario();
        Long inmueble = inmueble(municipioPrueba());

        Swipe creado = repository.guardar(Swipe.nuevo(usuario, inmueble, DecisionSwipe.LIKE));

        assertThat(creado.getId()).isNotNull();
        assertThat(creado.getCreadoEn()).isNotNull();
        assertThat(creado.getActualizadoEn()).isNotNull();
        assertThat(repository.porUsuarioEInmueble(usuario, inmueble))
                .hasValueSatisfying(swipe -> assertThat(swipe.getDecision())
                        .isEqualTo(DecisionSwipe.LIKE));

        Swipe cargado = repository.porUsuarioEInmueble(usuario, inmueble).orElseThrow();
        cargado.cambiarDecision(DecisionSwipe.NOPE);
        repository.guardar(cargado);
        assertThat(repository.porUsuarioEInmueble(usuario, inmueble))
                .hasValueSatisfying(swipe -> assertThat(swipe.getDecision())
                        .isEqualTo(DecisionSwipe.NOPE));

        repository.eliminar(cargado);
        assertThat(repository.porUsuarioEInmueble(usuario, inmueble)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private List<Long> idsDeck(Long usuario, int limite) {
        List<Long> ids = new ArrayList<>();
        repository.deck(usuario, null, limite).forEach(f -> ids.add(f.id()));
        return ids;
    }

    private String municipioPrueba() {
        return "It-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Long usuario() {
        return jdbc.queryForObject("""
                INSERT INTO usuarios (email, password_hash, nombre, rol, activo)
                VALUES (?, 'hash-de-prueba', 'Usuario Repo', 'USER', true)
                RETURNING id
                """, Long.class, "it-repo-" + UUID.randomUUID() + "@example.com");
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
