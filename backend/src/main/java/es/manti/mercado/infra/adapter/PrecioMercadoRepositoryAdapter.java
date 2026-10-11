package es.manti.mercado.infra.adapter;

import es.manti.mercado.domain.FilaPrecio;
import es.manti.mercado.domain.PrecioMercado;
import es.manti.mercado.domain.PrecioMercadoRepository;
import es.manti.mercado.domain.TipologiaVivienda;
import es.manti.mercado.domain.TipoPrecio;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador JPA del puerto {@link PrecioMercadoRepository}.
 *
 * <p>El upsert vive AQUÍ con {@code @Transactional} y no en el importador a
 * propósito: la descarga de los 37 MB ocurre antes, fuera de cualquier
 * transacción, de modo que una conexión del pool nunca espera a la red
 * (skill clean-code-servicios, regla 13).</p>
 *
 * <p>La escritura va por {@code JdbcTemplate.batchUpdate} en lotes de
 * {@value #TAMANO_LOTE} sentencias: mismo {@code INSERT ... ON CONFLICT} (y
 * misma idempotencia) que haría una a una, pero en pocos round-trips.</p>
 */
@Repository
@RequiredArgsConstructor
public class PrecioMercadoRepositoryAdapter implements PrecioMercadoRepository {

    /**
     * Upsert por clave natural. {@code fuente} no aparece a propósito: para las
     * filas nuevas toma su DEFAULT {@code 'MIVAU'} y las existentes conservan el suyo.
     */
    private static final String UPSERT = """
            INSERT INTO precios_mercado
                (codigo_ine, ambito, tipo, indicador, tipologia, anio, trimestre,
                 valor, unidad, n_testigos, provincia, fecha_descarga)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (codigo_ine, tipo, indicador, tipologia, anio, trimestre) DO UPDATE
               SET valor          = EXCLUDED.valor,
                   unidad         = EXCLUDED.unidad,
                   n_testigos     = EXCLUDED.n_testigos,
                   provincia      = EXCLUDED.provincia,
                   fecha_descarga = EXCLUDED.fecha_descarga,
                   updated_at     = now()
            """;

    /** Filas por lote JDBC: una importación completa son ~17 000. */
    static final int TAMANO_LOTE = 1_000;

    private final PrecioMercadoJpaRepository jpaRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<PrecioMercado> ultimaDe(String codigoIne, TipoPrecio tipo) {
        return jpaRepository.findFirstByCodigoIneAndTipoOrderByAnioDescTrimestreDesc(codigoIne, tipo);
    }

    @Override
    public List<PrecioMercado> deAnio(String codigoIne, TipoPrecio tipo, Short anio) {
        return jpaRepository.findByCodigoIneAndTipoAndAnio(codigoIne, tipo, anio);
    }

    @Override
    public Optional<PrecioMercado> porPeriodoYTipologia(String codigoIne, TipoPrecio tipo,
                                                        Short anio, Short trimestre,
                                                        TipologiaVivienda tipologia) {
        return jpaRepository.findByCodigoIneAndTipoAndAnioAndTrimestreAndTipologia(
                codigoIne, tipo, anio, trimestre, tipologia);
    }

    @Override
    @Transactional
    public int guardarOActualizar(List<FilaPrecio> filas, Instant fechaDescarga) {
        OffsetDateTime descarga = OffsetDateTime.ofInstant(fechaDescarga, ZoneOffset.UTC);
        int escritas = 0;
        for (int inicio = 0; inicio < filas.size(); inicio += TAMANO_LOTE) {
            List<FilaPrecio> lote =
                    filas.subList(inicio, Math.min(inicio + TAMANO_LOTE, filas.size()));
            escritas += jdbcTemplate.batchUpdate(UPSERT, new LoteUpsert(lote, descarga)).length;
        }
        return escritas;
    }

    @Override
    public long contar() {
        return jpaRepository.count();
    }

    /** Rellena cada sentencia del lote con la fila correspondiente. */
    private record LoteUpsert(List<FilaPrecio> filas, OffsetDateTime fechaDescarga)
            implements BatchPreparedStatementSetter {

        @Override
        public void setValues(PreparedStatement sentencia, int indice) throws SQLException {
            FilaPrecio fila = filas.get(indice);
            sentencia.setString(1, fila.codigoIne());
            sentencia.setString(2, fila.ambito().name());
            sentencia.setString(3, fila.tipo().name());
            sentencia.setString(4, fila.indicador().name());
            sentencia.setString(5, fila.tipologia().name());
            sentencia.setShort(6, fila.anio());
            sentencia.setShort(7, fila.trimestre());
            sentencia.setBigDecimal(8, fila.valor());
            sentencia.setString(9, fila.unidad().name());
            if (fila.nTestigos() == null) {
                sentencia.setNull(10, Types.INTEGER);
            } else {
                sentencia.setInt(10, fila.nTestigos());
            }
            if (fila.provincia() == null) {
                sentencia.setNull(11, Types.VARCHAR);
            } else {
                sentencia.setString(11, fila.provincia());
            }
            sentencia.setObject(12, fechaDescarga, Types.TIMESTAMP_WITH_TIMEZONE);
        }

        @Override
        public int getBatchSize() {
            return filas.size();
        }
    }
}
