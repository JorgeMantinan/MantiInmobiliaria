package es.manti.inmuebles.application;

import es.manti.inmuebles.domain.ParametroInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unitario del servicio de búsqueda: patrón ILIKE y validación de rangos. */
class BusquedaInmuebleServiceTest {

    @Test
    void patronMunicipio_ausenteOVacio_devuelveNull() {
        assertThat(BusquedaInmuebleService.patronMunicipio(null)).isNull();
        assertThat(BusquedaInmuebleService.patronMunicipio("   ")).isNull();
    }

    @Test
    void patronMunicipio_conValor_loEnvuelveEnComodinesYRecorta() {
        assertThat(BusquedaInmuebleService.patronMunicipio("  Madrid "))
                .isEqualTo("%Madrid%");
    }

    @Test
    void patronMunicipio_escapaLosComodinesDelLike() {
        assertThat(BusquedaInmuebleService.patronMunicipio("100%_a!"))
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

    private static BusquedaInmueblesCommand comando(BigDecimal min, BigDecimal max, Short habitaciones) {
        return new BusquedaInmueblesCommand(null, null, null, min, max, habitaciones,
                null, null, null, 20);
    }
}
