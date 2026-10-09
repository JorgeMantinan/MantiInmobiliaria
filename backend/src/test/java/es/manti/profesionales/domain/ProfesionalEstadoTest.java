package es.manti.profesionales.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regla de dominio de la moderación: un profesional sólo puede acabar en
 * {@code ACTIVO} o {@code RECHAZADO}; {@code PENDIENTE} es el estado inicial
 * del registro público y no se puede reasignar desde el backoffice (la API ya
 * lo bloquea con {@code @Pattern}, aquí se comprueba el núcleo).
 */
class ProfesionalEstadoTest {

    @Test
    void cambiarEstado_aActivo_loAdmite() {
        Profesional profesional = profesionalPendiente();

        profesional.cambiarEstado(EstadoProfesional.ACTIVO);

        assertThat(profesional.getEstado()).isEqualTo(EstadoProfesional.ACTIVO);
    }

    @Test
    void cambiarEstado_aRechazado_loAdmite() {
        Profesional profesional = profesionalPendiente();

        profesional.cambiarEstado(EstadoProfesional.RECHAZADO);

        assertThat(profesional.getEstado()).isEqualTo(EstadoProfesional.RECHAZADO);
    }

    @Test
    void cambiarEstado_aPendiente_lanzaEstadoProfesionalNoPermitido() {
        Profesional profesional = profesionalPendiente();

        assertThatThrownBy(() -> profesional.cambiarEstado(EstadoProfesional.PENDIENTE))
                .isInstanceOf(EstadoProfesionalNoPermitidoException.class)
                .hasMessageContaining("PENDIENTE")
                .hasMessageContaining("ACTIVO o RECHAZADO");
        assertThat(profesional.getEstado()).isEqualTo(EstadoProfesional.PENDIENTE);
    }

    private static Profesional profesionalPendiente() {
        return Profesional.nuevo("Profesional It", "it-pro-estado@example.com",
                null, "It-Municipio", List.of());
    }
}
