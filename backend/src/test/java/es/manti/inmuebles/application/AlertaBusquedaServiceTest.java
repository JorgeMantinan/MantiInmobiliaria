package es.manti.inmuebles.application;

import es.manti.inmuebles.domain.AlertaBusqueda;
import es.manti.inmuebles.domain.AlertaBusquedaRepository;
import es.manti.inmuebles.domain.AlertaDatos;
import es.manti.inmuebles.domain.AlertaNoEncontradaException;
import es.manti.inmuebles.domain.EstadoInmueble;
import es.manti.inmuebles.domain.InmuebleRepository;
import es.manti.inmuebles.domain.ParametroInvalidoException;
import es.manti.inmuebles.domain.TipoInmueble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unitario del CRUD de alertas (issue #96): marca de agua al alta, la
 * validación de rangos REUTILIZADA del listado y el owner-scoped (404).
 */
@ExtendWith(MockitoExtension.class)
class AlertaBusquedaServiceTest {

    @Mock
    private AlertaBusquedaRepository alertas;

    @Mock
    private InmuebleRepository inmuebles;

    private AlertaBusquedaService service;

    @BeforeEach
    void setUp() {
        service = new AlertaBusquedaService(alertas, inmuebles);
    }

    @Test
    void crear_inicializaLaAlertaActivaConLaMarcaDeAguaActual() {
        when(inmuebles.maximoId()).thenReturn(42L);
        when(alertas.cuantasPorUsuario(7L)).thenReturn(0L);
        when(alertas.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AlertaBusqueda creada = service.crear(7L, datos("Pisos", null, null));

        assertThat(creada.getId()).isNull();   // el id lo pone la BD al guardar
        assertThat(creada.getUltimoInmuebleAvisadoId()).isEqualTo(42L);
        assertThat(creada.isActiva()).isTrue();
        assertThat(creada.getUltimoAvisoEn()).isNull();
        verify(alertas).guardar(creada);
    }

    @Test
    void crear_conRangoInvalido_noTocaLaBaseDeDatos() {
        // La MISMA validación que el listado público (validarRangos).
        assertThatThrownBy(() -> service.crear(7L, datos("Raro",
                        new BigDecimal("500000"), new BigDecimal("100000"))))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("precioMin");

        verify(alertas, never()).guardar(any());
        verify(inmuebles, never()).maximoId();
    }

    @Test
    void actualizar_renombrarYpausar_modificaLaAlerta() {
        AlertaBusqueda alerta = AlertaBusqueda.alta(7L, datos("Original", null, null), 0L);
        when(alertas.porIdYUsuario(1L, 7L)).thenReturn(Optional.of(alerta));
        when(alertas.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AlertaBusqueda actualizada = service.actualizar(7L, 1L, "Nueva", false);

        assertThat(actualizada.getNombre()).isEqualTo("Nueva");
        assertThat(actualizada.isActiva()).isFalse();
    }

    @Test
    void actualizar_sinCambios_dejaLaAlertaIgual() {
        AlertaBusqueda alerta = AlertaBusqueda.alta(7L, datos("Original", null, null), 0L);
        when(alertas.porIdYUsuario(1L, 7L)).thenReturn(Optional.of(alerta));
        when(alertas.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AlertaBusqueda actualizada = service.actualizar(7L, 1L, null, null);

        assertThat(actualizada.getNombre()).isEqualTo("Original");
        assertThat(actualizada.isActiva()).isTrue();
    }

    @Test
    void actualizar_alertaAjena_oInexistente_devuelve404() {
        when(alertas.porIdYUsuario(9L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(7L, 9L, "Hack", null))
                .isInstanceOf(AlertaNoEncontradaException.class)
                .hasMessageContaining("9");
        verify(alertas, never()).guardar(any());
    }

    @Test
    void eliminar_alertaPropia_laBorra() {
        AlertaBusqueda alerta = AlertaBusqueda.alta(7L, datos("Bórrome", null, null), 0L);
        when(alertas.porIdYUsuario(1L, 7L)).thenReturn(Optional.of(alerta));

        service.eliminar(7L, 1L);

        verify(alertas).eliminar(alerta);
    }

    @Test
    void eliminar_alertaAjena_noLaBorra() {
        when(alertas.porIdYUsuario(9L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(7L, 9L))
                .isInstanceOf(AlertaNoEncontradaException.class);
        verify(alertas, never()).eliminar(any());
    }

    @Test
    void listar_delegaEnElRepositoritorio() {
        List<AlertaBusqueda> propias = List.of(
                AlertaBusqueda.alta(7L, datos("A", null, null), 0L));
        when(alertas.porUsuario(7L)).thenReturn(propias);

        assertThat(service.listar(7L)).isEqualTo(propias);
    }

    private static AlertaDatos datos(String nombre, BigDecimal precioMin, BigDecimal precioMax) {
        return new AlertaDatos(nombre, EstadoInmueble.VENTA, TipoInmueble.PISO,
                "Madrid", precioMin, precioMax, null, null);
    }
}
