package es.manti.inmuebles.application;

import es.manti.inmuebles.domain.AlertaBusquedaRepository;
import es.manti.inmuebles.domain.InmuebleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unitario del orquestador del barrido (follow-up #98): los dos
 * cortocircuitos (sin activas; catálogo por debajo de la MENOR marca) no
 * llegan a paginar, y el bucle pagina lotes hasta agotarlos.
 */
@ExtendWith(MockitoExtension.class)
class AlertaEvaluacionServiceTest {

    @Mock
    private AlertaBusquedaRepository alertas;

    @Mock
    private InmuebleRepository inmuebles;

    @Mock
    private AlertaLoteService loteService;

    private AlertaEvaluacionService service;

    @BeforeEach
    void setUp() {
        service = new AlertaEvaluacionService(alertas, inmuebles, loteService, 1);
    }

    @Test
    void evaluar_sinAlertasActivas_noBarreNada() {
        when(alertas.minimaMarcaActiva()).thenReturn(OptionalLong.empty());

        assertThat(service.evaluar()).isZero();

        verifyNoInteractions(inmuebles, loteService);
    }

    @Test
    void evaluar_maximoIdNoSuperaLaMenorMarca_noBarreNada() {
        when(alertas.minimaMarcaActiva()).thenReturn(OptionalLong.of(50L));
        when(inmuebles.maximoId()).thenReturn(50L);

        assertThat(service.evaluar()).isZero();

        verifyNoInteractions(loteService);
    }

    @Test
    void evaluar_conPendientes_paginaLotesHastaAgotarlosYSumeLosAvisos() {
        when(alertas.minimaMarcaActiva()).thenReturn(OptionalLong.of(10L));
        when(inmuebles.maximoId()).thenReturn(99L);
        when(loteService.evaluarLote(0L, 1))
                .thenReturn(new AlertaLoteService.Resultado(3L, 2));
        when(loteService.evaluarLote(3L, 1))
                .thenReturn(new AlertaLoteService.Resultado(7L, 1));
        when(loteService.evaluarLote(7L, 1))
                .thenReturn(AlertaLoteService.Resultado.VACIO);

        assertThat(service.evaluar()).isEqualTo(3);

        verify(loteService).evaluarLote(0L, 1);
        verify(loteService).evaluarLote(3L, 1);
        verify(loteService).evaluarLote(7L, 1);
    }
}
