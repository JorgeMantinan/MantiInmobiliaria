package es.manti.mercado.infra.adapter;

import es.manti.mercado.domain.PrecioMercado;
import es.manti.mercado.domain.TipologiaVivienda;
import es.manti.mercado.domain.TipoPrecio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA de {@code precios_mercado}. Sólo LECTURAS: la escritura es un
 * upsert nativo por lotes con {@link org.springframework.jdbc.core.JdbcTemplate}
 * ({@link PrecioMercadoRepositoryAdapter}), mucho más barato que pasar por el
 * session de Hibernate para ~17 000 filas.
 *
 * <p>Las tres lecturas son queries puntuales sobre
 * {@code idx_precios_mercado_ultima} (codigo_ine, tipo, anio DESC, trimestre DESC)
 * y sobre la UNIQUE de clave natural: nunca se recorre el histórico completo
 * (skill consultas-eficientes).</p>
 */
public interface PrecioMercadoJpaRepository extends JpaRepository<PrecioMercado, Long> {

    /** Último (anio, trimestre) publicado para un código y tipo. */
    Optional<PrecioMercado> findFirstByCodigoIneAndTipoOrderByAnioDescTrimestreDesc(
            String codigoIne, TipoPrecio tipo);

    /** Magnitudes de un código/tipo en un año (renta, cuantía y superficie). */
    List<PrecioMercado> findByCodigoIneAndTipoAndAnio(String codigoIne, TipoPrecio tipo, Short anio);

    /** Fila exacta de un periodo y tipología (régimen LIBRE del valor tasado). */
    Optional<PrecioMercado> findByCodigoIneAndTipoAndAnioAndTrimestreAndTipologia(
            String codigoIne, TipoPrecio tipo, Short anio, Short trimestre,
            TipologiaVivienda tipologia);
}
