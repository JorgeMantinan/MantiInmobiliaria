package es.manti.inmuebles.application;

import es.manti.inmuebles.domain.EstadoInmueble;
import es.manti.inmuebles.domain.InmuebleCursor;
import es.manti.inmuebles.domain.InmuebleOrden;
import es.manti.inmuebles.domain.InmuebleRepository;
import es.manti.inmuebles.domain.InmuebleResumen;
import es.manti.inmuebles.domain.ParametroInvalidoException;
import es.manti.inmuebles.domain.TipoInmueble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unitario del servicio de búsqueda: patrón ILIKE, validación de rangos y
 *  delegación del total a la caché de conteos (issue #27). */
@ExtendWith(MockitoExtension.class)
class BusquedaInmuebleServiceTest {

    @Mock
    private InmuebleRepository repository;

    @Mock
    private ConteoInmueblesCache conteoCache;

    private BusquedaInmuebleService service;

    @BeforeEach
    void setUp() {
        service = new BusquedaInmuebleService(repository, conteoCache);
    }

    @Test
    void patronMunicipio_ausenteOVacio_devuelveNull() {
        assertThat(BusquedaInmuebleService.patronMunicipio(null)).isNull();
        assertThat(BusquedaInmuebleService.patronMunicipio("   ")).isNull();
    }

    @Test
    void patronMunicipio_conValor_loEnvuelveEnComodinesYRecortaEnMinusculas() {
        // El plegado de mayúsculas se hace en Java: la query usa
        // LOWER(columna) LIKE ? sin LOWER(:p) (bind JAVA_OBJECT→bytea en null).
        assertThat(BusquedaInmuebleService.patronMunicipio("  Madrid "))
                .isEqualTo("%madrid%");
    }

    @Test
    void patronMunicipio_escapaLosComodinesDelLike() {
        // El escape va ANTES del toLowerCase: '!' no se altera al bajar el patrón.
        assertThat(BusquedaInmuebleService.patronMunicipio("100%_A!"))
                .isEqualTo("%100!%!_a!!%");
    }

    // ------------------------------------------------------------------
    // Validación de rangos (H3): 400 con errors:[{field, message}]
    // ------------------------------------------------------------------

    @Test
    void validarRangos_precioMinMayorQuePrecioMax_lanzaParametroInvalido() {
        assertThatThrownBy(() -> BusquedaInmuebleService.validarRangos(
                comando(new BigDecimal("500000"), new BigDecimal("100000"), null)))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("precioMin");

        assertThat(buscarCampo(new BigDecimal("500000"), new BigDecimal("100000"), null))
                .isEqualTo("precioMin");
    }

    @Test
    void validarRangos_precioNegativo_lanzaParametroInvalido() {
        assertThat(buscarCampo(new BigDecimal("-1"), null, null)).isEqualTo("precioMin");
        assertThat(buscarCampo(null, new BigDecimal("-0.01"), null)).isEqualTo("precioMax");
    }

    @Test
    void validarRangos_habitacionesNegativas_lanzaParametroInvalido() {
        assertThat(buscarCampo(null, null, (short) -1)).isEqualTo("habitacionesMin");
    }

    @Test
    void validarRangos_rangoValido_noLanza() {
        assertThatCode(() -> BusquedaInmuebleService.validarRangos(
                comando(new BigDecimal("100000"), new BigDecimal("500000"), (short) 0)))
                .doesNotThrowAnyException();
        assertThatCode(() -> BusquedaInmuebleService.validarRangos(
                comando(null, null, null))).doesNotThrowAnyException();
    }

    private static String buscarCampo(BigDecimal min, BigDecimal max, Short habitaciones) {
        try {
            BusquedaInmuebleService.validarRangos(comando(min, max, habitaciones));
            return null;
        } catch (ParametroInvalidoException e) {
            return e.parametro();
        }
    }

    // ------------------------------------------------------------------
    // totalElements vía caché de conteos (issue #27)
    // ------------------------------------------------------------------

    @Test
    void listar_primeraPagina_elTotalLoAportaLaCache() {
        InmuebleResumen fila = resumen(1L);
        when(repository.listar(any(), any(), isNull(), anyInt())).thenReturn(List.of(fila));
        when(conteoCache.contar(any())).thenReturn(42L);

        ListadoInmuebles resultado = service.listar(comando(null, null, null, null));

        assertThat(resultado.totalElements()).isEqualTo(42L);
        assertThat(resultado.contenido()).containsExactly(fila);
        verify(conteoCache).contar(any());
        // El puerto ya no cuenta: sólo la caché (que internamente sí lo haría
        // en un miss) decide cuándo tocar la base de datos.
        verify(repository, never()).contar(any());
    }

    @Test
    void listar_conCursor_noPagaNiCacheaElConteo() {
        InmuebleResumen fila = resumen(1L);
        when(repository.listar(any(), any(), any(InmuebleCursor.class), anyInt()))
                .thenReturn(List.of(fila));
        String cursor = new InmuebleCursor(InmuebleOrden.CREADO_DESC,
                Instant.parse("2026-01-01T00:00:00Z"), null, 1L).codificar();

        ListadoInmuebles resultado = service.listar(comando(null, null, null, cursor));

        assertThat(resultado.totalElements()).isNull();
        verify(conteoCache, never()).contar(any());
    }

    private static InmuebleResumen resumen(long id) {
        return new InmuebleResumen(id, "Piso " + id, new BigDecimal("200000"),
                TipoInmueble.PISO, EstadoInmueble.VENTA, 90, (short) 3, (short) 1,
                "Madrid", "Madrid", null, false, 40.4, -3.7,
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static BusquedaInmueblesCommand comando(BigDecimal min, BigDecimal max,
                                                    Short habitaciones, String cursor) {
        return new BusquedaInmueblesCommand(null, null, null, min, max, habitaciones,
                null, null, cursor, 20);
    }

    private static BusquedaInmueblesCommand comando(BigDecimal min, BigDecimal max, Short habitaciones) {
        return new BusquedaInmueblesCommand(null, null, null, min, max, habitaciones,
                null, null, null, 20);
    }
}
