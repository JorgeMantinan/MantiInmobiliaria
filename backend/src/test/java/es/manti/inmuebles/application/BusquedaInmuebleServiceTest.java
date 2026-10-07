package es.manti.inmuebles.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Patrón ILIKE del filtro de municipio (unitario, sin contexto Spring). */
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
}
