package es.manti.inmuebles.application;

import es.manti.inmuebles.domain.EstadoInmueble;
import es.manti.inmuebles.domain.Inmueble;
import es.manti.inmuebles.domain.InmuebleDatos;
import es.manti.inmuebles.domain.InmuebleRepository;
import es.manti.inmuebles.domain.TipoInmueble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unitario del servicio de backoffice (issue #27): toda escritura de inmuebles
 * invalida la caché de conteos del listado público tras el commit.
 */
@ExtendWith(MockitoExtension.class)
class GestionInmuebleServiceTest {

    @Mock
    private InmuebleRepository repository;

    @Mock
    private ConteoInmueblesCache conteoCache;

    private GestionInmuebleService service;

    @BeforeEach
    void setUp() {
        service = new GestionInmuebleService(repository, conteoCache);
    }

    @Test
    void crear_invalidaElCacheDeConteos() {
        when(repository.guardar(any(Inmueble.class))).thenAnswer(inv -> inv.getArgument(0));

        service.crear(datos());

        verify(conteoCache).invalidarTrasCommit();
    }

    @Test
    void actualizar_invalidaElCacheDeConteos() {
        Inmueble existente = Inmueble.nuevo(datos());
        when(repository.porIdIncluyendoEliminados(1L)).thenReturn(Optional.of(existente));
        when(repository.guardar(existente)).thenReturn(existente);

        service.actualizar(1L, datos());

        verify(conteoCache).invalidarTrasCommit();
    }

    @Test
    void eliminar_invalidaElCacheDeConteos() {
        Inmueble existente = Inmueble.nuevo(datos());
        when(repository.porIdIncluyendoEliminados(1L)).thenReturn(Optional.of(existente));
        when(repository.guardar(existente)).thenReturn(existente);

        service.eliminar(1L);

        verify(conteoCache).invalidarTrasCommit();
    }

    private static InmuebleDatos datos() {
        return new InmuebleDatos("Piso de prueba", "Descripción", new BigDecimal("200000"),
                90, (short) 3, (short) 1, TipoInmueble.PISO, EstadoInmueble.VENTA,
                "Calle Mayor 1", "28013", "Madrid", "Madrid", 40.4, -3.7,
                "https://picsum.photos/seed/it/800/600", false);
    }
}
