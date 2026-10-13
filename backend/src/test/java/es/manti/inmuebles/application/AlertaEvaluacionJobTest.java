package es.manti.inmuebles.application;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * El job programado es una cáscara: sólo invoca al servicio (issue #96). Se
 * instancia A MANO porque en el perfil de test el bean no existe
 * ({@code manti.alertas.enabled=false}); así la clase queda cubierta igual.
 */
class AlertaEvaluacionJobTest {

    @Test
    void evaluar_delegaEnElServicioDeEvaluacion() {
        AlertaEvaluacionService evaluacion = mock(AlertaEvaluacionService.class);

        new AlertaEvaluacionJob(evaluacion).evaluar();

        verify(evaluacion).evaluar();
    }
}
