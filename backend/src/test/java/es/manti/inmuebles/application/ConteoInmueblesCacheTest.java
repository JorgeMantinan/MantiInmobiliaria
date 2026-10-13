package es.manti.inmuebles.application;

import es.manti.inmuebles.domain.InmuebleFiltros;
import es.manti.inmuebles.domain.InmuebleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unitario de la caché de conteos (issue #27): aciertos/fracasos de clave,
 * normalización de precios y evicción diferida hasta el commit.
 */
@ExtendWith(MockitoExtension.class)
class ConteoInmueblesCacheTest {

    @Mock
    private InmuebleRepository repository;

    private ConteoInmueblesCache cache;

    @BeforeEach
    void setUp() {
        cache = new ConteoInmueblesCache(repository, Duration.ofMinutes(2), 100);
    }

    @AfterEach
    void limpiarSincronizacion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void contar_losMismosFiltros_consultaElRepositorioUnaSolaVez() {
        InmuebleFiltros filtros = new InmuebleFiltros(null, null, "%madrid%",
                new BigDecimal("100000"), null, null, null);
        when(repository.contar(filtros)).thenReturn(7L);

        assertThat(cache.contar(filtros)).isEqualTo(7L);
        assertThat(cache.contar(filtros)).isEqualTo(7L);

        verify(repository, times(1)).contar(filtros);
    }

    @Test
    void contar_preciosConDistintaEscala_compartenLaMismaClave() {
        // 100000, 100000.0 y 100000.00 son el mismo filtro: sin normalizar,
        // BigDecimal.equals daría tres claves distintas para un solo COUNT.
        when(repository.contar(new InmuebleFiltros(null, null, null,
                new BigDecimal("100000"), null, null, null))).thenReturn(3L);

        assertThat(cache.contar(new InmuebleFiltros(null, null, null,
                new BigDecimal("100000"), null, null, null))).isEqualTo(3L);
        assertThat(cache.contar(new InmuebleFiltros(null, null, null,
                new BigDecimal("100000.00"), null, null, null))).isEqualTo(3L);

        verify(repository, times(1)).contar(any());
    }

    @Test
    void contar_filtrosDistintos_noCompartenEntrada() {
        InmuebleFiltros sinFiltros = new InmuebleFiltros(null, null, null, null, null, null, null);
        InmuebleFiltros conMunicipio = new InmuebleFiltros(null, null, "%madrid%",
                null, null, null, null);
        when(repository.contar(sinFiltros)).thenReturn(10L);
        when(repository.contar(conMunicipio)).thenReturn(4L);

        assertThat(cache.contar(sinFiltros)).isEqualTo(10L);
        assertThat(cache.contar(conMunicipio)).isEqualTo(4L);

        verify(repository).contar(sinFiltros);
        verify(repository).contar(conMunicipio);
    }

    @Test
    void invalidarTrasCommit_sinTransaccionEvictaDeInmediato() {
        InmuebleFiltros filtros = new InmuebleFiltros(null, null, null, null, null, null, null);
        when(repository.contar(filtros)).thenReturn(1L, 2L);

        assertThat(cache.contar(filtros)).isEqualTo(1L);
        cache.invalidarTrasCommit();
        assertThat(cache.contar(filtros)).isEqualTo(2L);

        verify(repository, times(2)).contar(filtros);
    }

    @Test
    void invalidarTrasCommit_enTransaccion_soloEvictaTrasElCommit() {
        InmuebleFiltros filtros = new InmuebleFiltros(null, null, null, null, null, null, null);
        when(repository.contar(filtros)).thenReturn(1L, 2L);
        assertThat(cache.contar(filtros)).isEqualTo(1L);

        // Simula la transacción activa del servicio de escritura.
        TransactionSynchronizationManager.initSynchronization();
        cache.invalidarTrasCommit();

        // Aún sin commitear la entrada sigue viva: no se re-cachea el total viejo.
        assertThat(cache.contar(filtros)).isEqualTo(1L);
        verify(repository, times(1)).contar(filtros);

        // Tras el commit (afterCommit) la caché queda vacía y se repuebla.
        List<TransactionSynchronization> sincronizaciones =
                TransactionSynchronizationManager.getSynchronizations();
        assertThat(sincronizaciones).hasSize(1);
        sincronizaciones.forEach(TransactionSynchronization::afterCommit);

        assertThat(cache.contar(filtros)).isEqualTo(2L);
        verify(repository, times(2)).contar(filtros);
    }
}
