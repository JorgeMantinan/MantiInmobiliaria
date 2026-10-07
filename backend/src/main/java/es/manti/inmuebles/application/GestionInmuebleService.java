package es.manti.inmuebles.application;

import es.manti.inmuebles.domain.Inmueble;
import es.manti.inmuebles.domain.InmuebleDatos;
import es.manti.inmuebles.domain.InmuebleNoEncontradoException;
import es.manti.inmuebles.domain.InmuebleRepository;
import es.manti.inmuebles.domain.InmuebleYaEliminadoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de ESCRITURA de inmuebles (backoffice ADMIN): alta, actualización
 * total y borrado lógico. La autorización la aplica el controller
 * ({@code @PreAuthorize}); aquí sólo vive la transaccionalidad y las reglas.
 *
 * <p>El lookup de escritura NO filtra {@code eliminado_en} para poder separar el
 * 404 (no existe) del 409 (ya eliminado); la API pública sigue usando
 * {@link es.manti.inmuebles.domain.InmuebleRepository#porId(Long)}, que sí filtra
 * y devuelve 404 indistinguible.</p>
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
        Inmueble inmueble = buscarParaEscritura(id);
        inmueble.actualizar(datos);
        Inmueble guardado = repository.guardar(inmueble);
        log.info("Inmueble {} actualizado", id);
        return guardado;
    }

    /** Borrado lógico: marca `eliminado_en` y desaparece de la API pública. */
    @Transactional
    public void eliminar(Long id) {
        Inmueble inmueble = buscarParaEscritura(id);
        inmueble.eliminar();
        repository.guardar(inmueble);
        log.info("Inmueble {} eliminado (soft-delete)", id);
    }

    /** 404 si no existe; 409 si ya estaba eliminado (no se reescribe un borrado). */
    private Inmueble buscarParaEscritura(Long id) {
        Inmueble inmueble = repository.porIdIncluyendoEliminados(id)
                .orElseThrow(() -> new InmuebleNoEncontradoException(id));
        if (inmueble.getEliminadoEn() != null) {
            throw new InmuebleYaEliminadoException(id);
        }
        return inmueble;
    }
}
