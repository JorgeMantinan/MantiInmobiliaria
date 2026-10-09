package es.manti.inmuebles;

import es.manti.inmuebles.domain.EstadoInmueble;
import es.manti.inmuebles.domain.Inmueble;
import es.manti.inmuebles.domain.InmuebleCursor;
import es.manti.inmuebles.domain.InmuebleDatos;
import es.manti.inmuebles.domain.InmuebleFiltros;
import es.manti.inmuebles.domain.InmuebleOrden;
import es.manti.inmuebles.domain.InmuebleRepository;
import es.manti.inmuebles.domain.InmuebleResumen;
import es.manti.inmuebles.domain.TipoInmueble;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integración del repositorio de inmuebles contra PostgreSQL real
 * (mismo patrón que la FASE 1: perfil {@code test} + datasource real).
 * Los fixtures se marcan con municipio {@code It-*} para limpiarlos tras cada test.
 */
@SpringBootTest
@ActiveProfiles("test")
class InmuebleRepositoryTest {

    @Autowired
    private InmuebleRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiarFixtures() {
        jdbc.update("DELETE FROM inmuebles WHERE municipio LIKE 'It-%'");
    }

    @Test
    void listar_sinOtrosFiltros_devuelveVigentesOrdenadosPorCreadoDesc() {
        String municipio = municipioPrueba();
        Inmueble primero = crear(municipio, "it-uno", "100000");
        Inmueble segundo = crear(municipio, "it-dos", "200000");
        InmuebleFiltros soloMunicipio = new InmuebleFiltros(null, null, patron(municipio),
                null, null, null, null);

        List<InmuebleResumen> filas = repository.listar(
                soloMunicipio, InmuebleOrden.CREADO_DESC, null, 10);

        assertThat(filas).extracting(InmuebleResumen::id)
                .containsExactly(segundo.getId(), primero.getId());
    }

    @Test
    void listar_porPrecioAsc_devuelveOrdenAscendenteYDesempataPorId() {
        String municipio = municipioPrueba();
        InmuebleFiltros soloMunicipio = new InmuebleFiltros(null, null, patron(municipio),
                null, null, null, null);
        Inmueble alto = crear(municipio, "it-alto", "300000");
        Inmueble bajo = crear(municipio, "it-bajo", "100000");
        Inmueble medio = crear(municipio, "it-medio", "200000");

        List<InmuebleResumen> filas = repository.listar(
                soloMunicipio, InmuebleOrden.PRECIO_ASC, null, 10);

        assertThat(filas).extracting(InmuebleResumen::id)
                .containsExactly(bajo.getId(), medio.getId(), alto.getId());
    }

    @Test
    void listar_porPrecioDesc_devuelveOrdenDescendente() {
        String municipio = municipioPrueba();
        InmuebleFiltros soloMunicipio = new InmuebleFiltros(null, null, patron(municipio),
                null, null, null, null);
        Inmueble alto = crear(municipio, "it-alto", "300000");
        Inmueble bajo = crear(municipio, "it-bajo", "100000");
        Inmueble medio = crear(municipio, "it-medio", "200000");

        List<InmuebleResumen> filas = repository.listar(
                soloMunicipio, InmuebleOrden.PRECIO_DESC, null, 10);

        assertThat(filas).extracting(InmuebleResumen::id)
                .containsExactly(alto.getId(), medio.getId(), bajo.getId());
    }

    @Test
    void listar_conFiltrosCombinados_devuelveSoloCoincidencias() {
        String municipio = municipioPrueba();
        Inmueble coincide = crear(municipio, "it-coincide", "250000",
                EstadoInmueble.VENTA, TipoInmueble.PISO, (short) 3, true);
        crear(municipio, "it-alquiler", "900", EstadoInmueble.ALQUILER, TipoInmueble.PISO, (short) 3, true);
        crear(municipio, "it-casa", "250000", EstadoInmueble.VENTA, TipoInmueble.CASA, (short) 3, true);
        crear(municipio, "it-precio-alto", "900000", EstadoInmueble.VENTA, TipoInmueble.PISO, (short) 3, true);
        crear(municipio, "it-pocas-hab", "250000", EstadoInmueble.VENTA, TipoInmueble.PISO, (short) 1, true);
        crear(municipio, "it-no-destacado", "250000", EstadoInmueble.VENTA, TipoInmueble.PISO, (short) 3, false);

        InmuebleFiltros filtros = new InmuebleFiltros(EstadoInmueble.VENTA, TipoInmueble.PISO,
                patron(municipio), null, new BigDecimal("300000"), (short) 2, true);
        List<InmuebleResumen> filas = repository.listar(filtros, InmuebleOrden.CREADO_DESC, null, 10);

        assertThat(filas).extracting(InmuebleResumen::id).containsExactly(coincide.getId());
        assertThat(repository.contar(filtros)).isEqualTo(1);
    }

    /**
     * Regresión hotfix: todos los parámetros a null (listado sin filtros). El
     * patrón de municipio NULL bindeado como JAVA_OBJECT→bytea rompía
     * {@code LOWER(:p)} con {@code function lower(bytea) does not exist}.
     */
    @Test
    void listar_y_contar_sinFiltros_devuelveVigentes() {
        String municipio = municipioPrueba();
        Inmueble creado = crear(municipio, "it-sin-filtros", "100000");
        InmuebleFiltros sinFiltros = new InmuebleFiltros(null, null, null, null, null, null, null);

        List<InmuebleResumen> filas = repository.listar(
                sinFiltros, InmuebleOrden.CREADO_DESC, null, 10);

        assertThat(filas).extracting(InmuebleResumen::id).contains(creado.getId());
        assertThat(repository.contar(sinFiltros)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void listar_porMunicipioParcial_esCaseInsensitive() {
        // Contrato de InmuebleFiltros: el patrón viaja ya en minúsculas (lo
        // plega la capa de aplicación) y la columna conserva su caso original:
        // LOWER(col) LIKE patrón sigue casando con entradas en mayúsculas.
        String municipio = municipioPrueba();
        Inmueble creado = crear(municipio, "it-casa", "150000");

        List<InmuebleResumen> minusculas = repository.listar(
                new InmuebleFiltros(null, null, patron(municipio.toUpperCase()),
                        null, null, null, null),
                InmuebleOrden.CREADO_DESC, null, 10);

        assertThat(minusculas).extracting(InmuebleResumen::id).containsExactly(creado.getId());
        assertThat(repository.contar(new InmuebleFiltros(null, null, "%no-existe-xyz%",
                null, null, null, null))).isZero();
    }

    @Test
    void listar_y_porId_excluyenLosInmueblesEliminados() {
        String municipio = municipioPrueba();
        InmuebleFiltros soloMunicipio = new InmuebleFiltros(null, null, patron(municipio),
                null, null, null, null);
        Inmueble vigente = crear(municipio, "it-vigente", "100000");
        Inmueble borrado = crear(municipio, "it-borrado", "100000");
        borrado.eliminar();
        repository.guardar(borrado);

        List<InmuebleResumen> filas = repository.listar(
                soloMunicipio, InmuebleOrden.CREADO_DESC, null, 10);

        assertThat(filas).extracting(InmuebleResumen::id)
                .contains(vigente.getId())
                .doesNotContain(borrado.getId());
        assertThat(repository.porId(borrado.getId())).isEmpty();
        assertThat(repository.porId(vigente.getId())).isPresent();
        assertThat(repository.contar(soloMunicipio)).isEqualTo(1);
    }

    @Test
    void listar_conCursor_keysetNoSeSolapaNiOmiteFilas() {
        String municipio = municipioPrueba();
        InmuebleFiltros filtros = new InmuebleFiltros(null, null, patron(municipio),
                null, null, null, null);
        for (int i = 1; i <= 5; i++) {
            crear(municipio, "it-" + i, String.valueOf(100000 + i * 1000));
        }

        List<InmuebleResumen> pagina1 = repository.listar(filtros, InmuebleOrden.CREADO_DESC, null, 2);
        InmuebleResumen ultima = pagina1.get(pagina1.size() - 1);
        List<InmuebleResumen> pagina2 = repository.listar(filtros, InmuebleOrden.CREADO_DESC,
                new InmuebleCursor(InmuebleOrden.CREADO_DESC, ultima.createdAt(), null, ultima.id()), 2);
        InmuebleResumen ultima2 = pagina2.get(pagina2.size() - 1);
        List<InmuebleResumen> pagina3 = repository.listar(filtros, InmuebleOrden.CREADO_DESC,
                new InmuebleCursor(InmuebleOrden.CREADO_DESC, ultima2.createdAt(), null, ultima2.id()), 2);

        assertThat(pagina1).hasSize(2);
        assertThat(pagina2).hasSize(2);
        assertThat(pagina3).hasSize(1);
        List<Long> ids = new java.util.ArrayList<>();
        pagina1.forEach(f -> ids.add(f.id()));
        pagina2.forEach(f -> ids.add(f.id()));
        pagina3.forEach(f -> ids.add(f.id()));
        assertThat(ids).hasSize(5).doesNotHaveDuplicates();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private String municipioPrueba() {
        return "It-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * El patrón de municipio viaja ya en minúsculas (contrato de
     * {@code InmuebleFiltros}): el plegado de mayúsculas lo hace la capa de
     * aplicación ({@code BusquedaInmuebleService.patronMunicipio}), no
     * {@code LOWER(:p)} en la query (bind JAVA_OBJECT→bytea con parámetro nulo).
     */
    private static String patron(String municipio) {
        return "%" + municipio.toLowerCase(Locale.ROOT) + "%";
    }

    private Inmueble crear(String municipio, String titulo, String precio) {
        return crear(municipio, titulo, precio, EstadoInmueble.VENTA, TipoInmueble.PISO, (short) 2, false);
    }

    private Inmueble crear(String municipio, String titulo, String precio, EstadoInmueble estado,
                           TipoInmueble tipo, Short habitaciones, boolean destacado) {
        InmuebleDatos datos = new InmuebleDatos(
                titulo, "Descripción de prueba para el repositorio", new BigDecimal(precio), 90,
                habitaciones, (short) 1, tipo, estado, "Calle de prueba 1", "28001",
                municipio, "Madrid", 40.4, -3.7, "https://picsum.photos/seed/it/800/600", destacado);
        return repository.guardar(Inmueble.nuevo(datos));
    }
}
