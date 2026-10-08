package es.manti.profesionales.infra.adapter;

import es.manti.profesionales.domain.Gremio;
import es.manti.profesionales.domain.GremioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adaptador JPA del puerto {@link GremioRepository}.
 *
 * <p>La búsqueda por slug se hace en memoria sobre el catálogo completo (12
 * filas, cacheadas además a nivel HTTP 60 s) para poder comparar SIN acentos:
 * el contrato de la API admite tanto {@code albañil} como {@code albanil} y en
 * SQL eso exigiría la extensión {@code unaccent} o un índice expresado. Con 12
 * filas el coste es despreciable y la lógica queda en
 * {@link Gremio#normalizarSlug(String)}, compartida con el servicio.</p>
 */
@Repository
@RequiredArgsConstructor
public class GremioRepositoryAdapter implements GremioRepository {

    private final GremioJpaRepository jpaRepository;

    @Override
    public List<Gremio> todos() {
        return jpaRepository.findAllByOrderByNombreAsc();
    }

    @Override
    public List<Gremio> porSlugs(Collection<String> slugs) {
        Set<String> pedidos = slugs.stream()
                .map(Gremio::normalizarSlug)
                .collect(Collectors.toSet());
        return todos().stream()
                .filter(gremio -> pedidos.contains(Gremio.normalizarSlug(gremio.getSlug())))
                .toList();
    }
}
