package es.manti.mercado.infra.adapter;

import es.manti.mercado.domain.FilaPrecio;
import es.manti.mercado.domain.PrecioMercado;
import es.manti.mercado.domain.PrecioMercadoRepository;
import es.manti.mercado.domain.TipologiaVivienda;
import es.manti.mercado.domain.TipoPrecio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador JPA del puerto {@link PrecioMercadoRepository}.
 *
 * <p>El upsert vive AQUÍ con {@code @Transactional} y no en el importador a
 * propósito: la descarga de los 37 MB ocurre antes, fuera de cualquier
 * transacción, de modo que una conexión del pool nunca espera a la red
 * (skill clean-code-servicios, regla 13).</p>
 */
@Repository
@RequiredArgsConstructor
public class PrecioMercadoRepositoryAdapter implements PrecioMercadoRepository {

    private final PrecioMercadoJpaRepository jpaRepository;

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
        int escritas = 0;
        for (FilaPrecio fila : filas) {
            escritas += jpaRepository.upsert(
                    fila.codigoIne(),
                    fila.ambito().name(),
                    fila.tipo().name(),
                    fila.indicador().name(),
                    fila.tipologia().name(),
                    fila.anio(),
                    fila.trimestre(),
                    fila.valor(),
                    fila.unidad().name(),
                    fila.nTestigos(),
                    fila.provincia(),
                    fechaDescarga);
        }
        return escritas;
    }

    @Override
    public long contar() {
        return jpaRepository.count();
    }
}
