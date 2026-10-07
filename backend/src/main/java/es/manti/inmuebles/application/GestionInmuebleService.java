package es.manti.inmuebles.application;

import es.manti.inmuebles.domain.Inmueble;
import es.manti.inmuebles.domain.InmuebleDatos;
import es.manti.inmuebles.domain.InmuebleNoEncontradoException;
import es.manti.inmuebles.domain.InmuebleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de ESCRITURA de inmuebles (backoffice ADMIN): alta, actualización
 * total y borrado lógico. La autorización la aplica el controller
 * ({@code @PreAuthorize}); aquí sólo vive la transaccionalidad y las reglas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GestionInmuebleService {

    private final InmuebleRepository repository;

    @Transactional
    public Inmueble crear(InmuebleDatos datos) {
        Inmueble inmueble = repository.guardar(Inmueble.nuevo(datos));
        log.info("Inmueble {} creado ({}, {})", inmueble.getId(), inmueble.getTitulo(), inmueble.getEstado());
        return inmueble;
    }

    @Transactional
    public Inmueble actualizar(Long id, InmuebleDatos datos) {
        Inmueble inmueble = repository.porId(id)
                .orElseThrow(() -> new InmuebleNoEncontradoException(id));
        inmueble.actualizar(datos);
        Inmueble guardado = repository.guardar(inmueble);
        log.info("Inmueble {} actualizado", id);
        return guardado;
    }

    /** Borrado lógico: marca {@code eliminado_en} y desaparece de la API pública. */
    @Transactional
    public void eliminar(Long id) {
        Inmueble inmueble = repository.porId(id)
                .orElseThrow(() -> new InmuebleNoEncontradoException(id));
        inmueble.eliminar();
        repository.guardar(inmueble);
        log.info("Inmueble {} eliminado (soft-delete)", id);
    }
}
