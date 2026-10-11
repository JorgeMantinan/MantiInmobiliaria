package es.manti.mercado.infra.adapter;

import es.manti.mercado.domain.PrecioMercado;
import es.manti.mercado.domain.TipologiaVivienda;
import es.manti.mercado.domain.TipoPrecio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA de {@code precios_mercado}.
 *
 * <p>Las lecturas son tres queries puntuales sobre
 * {@code idx_precios_mercado_ultima} (codigo_ine, tipo, anio DESC, trimestre DESC)
 * y sobre la UNIQUE de clave natural: nunca se recorre el histórico completo
 * (skill consultas-eficientes).</p>
 *
 * <p>La escritura es un <b>upsert nativo {@code INSERT ... ON CONFLICT}</b> — la
 * forma canónica de PostgreSQL de resolver la clave natural sin SELECT previo
 * (16 000 filas importadas ⇒ 16 000 idas y vueltas en vez de 32 000).</p>
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

    /**
     * {@code fuente} no aparece a propósito: para las filas nuevas toma su
     * DEFAULT {@code 'MIVAU'} y las existentes conservan el suyo.
     */
    @Modifying
    @Query(value = """
            INSERT INTO precios_mercado
                (codigo_ine, ambito, tipo, indicador, tipologia, anio, trimestre,
                 valor, unidad, n_testigos, provincia, fecha_descarga)
            VALUES
                (:codigoIne, :ambito, :tipo, :indicador, :tipologia, :anio, :trimestre,
                 :valor, :unidad, :nTestigos, :provincia, :fechaDescarga)
            ON CONFLICT (codigo_ine, tipo, indicador, tipologia, anio, trimestre) DO UPDATE
               SET valor          = EXCLUDED.valor,
                   unidad         = EXCLUDED.unidad,
                   n_testigos     = EXCLUDED.n_testigos,
                   provincia      = EXCLUDED.provincia,
                   fecha_descarga = EXCLUDED.fecha_descarga,
                   updated_at     = now()
            """, nativeQuery = true)
    int upsert(@Param("codigoIne") String codigoIne,
               @Param("ambito") String ambito,
               @Param("tipo") String tipo,
               @Param("indicador") String indicador,
               @Param("tipologia") String tipologia,
               @Param("anio") short anio,
               @Param("trimestre") short trimestre,
               @Param("valor") BigDecimal valor,
               @Param("unidad") String unidad,
               @Param("nTestigos") Integer nTestigos,
               @Param("provincia") String provincia,
               @Param("fechaDescarga") Instant fechaDescarga);
}
