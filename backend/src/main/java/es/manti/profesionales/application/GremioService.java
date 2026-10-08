package es.manti.profesionales.application;

import es.manti.profesionales.domain.Gremio;
import es.manti.profesionales.domain.GremioInexistenteException;
import es.manti.profesionales.domain.GremioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catálogo de gremios: lectura pública del listado y resolución de los slugs
 * pedidos por los formularios (solicitud y registro de profesional).
 */
@Service
@RequiredArgsConstructor
public class GremioService {

    /** Máximo de gremios por petición, también validado en el DTO de la API. */
    private static final int MAXIMO_POR_PETICION = 8;

    private final GremioRepository repository;

    /** Catálogo completo ordenado por nombre (12 filas). */
    @Transactional(readOnly = true)
    public List<Gremio> listar() {
        return repository.todos();
    }

    /**
     * Resuelve los slugs pedidos a entidades del catálogo, deduplicándolos y
     * comprobando que existan todos.
     *
     * <p>Se integra en la transacción del llamante (propagación REQUIRED): la
     * resolución y la escritura posterior forman un único unit of work.</p>
     *
     * @throws GremioInexistenteException 400 si algún slug no está en el
     *                                    catálogo o la lista queda vacía
     */
    @Transactional
    public List<Gremio> resolver(List<String> slugs) {
        Set<String> pedidos = new LinkedHashSet<>(slugs == null ? List.of() : slugs);
        if (pedidos.isEmpty() || pedidos.size() > MAXIMO_POR_PETICION) {
            throw new GremioInexistenteException(List.copyOf(pedidos));
        }
        List<Gremio> encontrados = repository.porSlugs(pedidos);
        Set<String> resueltos = encontrados.stream()
                .map(gremio -> Gremio.normalizarSlug(gremio.getSlug()))
                .collect(Collectors.toSet());
        List<String> faltantes = pedidos.stream()
                .filter(slug -> !resueltos.contains(Gremio.normalizarSlug(slug)))
                .toList();
        if (!faltantes.isEmpty()) {
            throw new GremioInexistenteException(faltantes);
        }
        return encontrados;
    }
}
