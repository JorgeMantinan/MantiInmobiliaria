package es.manti.inmuebles.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import es.manti.inmuebles.domain.InmuebleFiltros;
import es.manti.inmuebles.domain.InmuebleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

/**
 * Caché Caffeine del {@code COUNT(*)} que alimenta {@code totalElements} del
 * listado público (issue #27). Toda primera página paga un COUNT con 7 guardas
 * {@code (:param IS NULL OR ...)}; el resultado es público, estable entre
 * escrituras (escasas: sólo el backoffice) y admite una ventana de
 * staleness acotada.
 *
 * <p><b>Clave:</b> el propio record {@link InmuebleFiltros}, normalizado en su
 * constructor compacto (precios con escala canónica), así
 * {@code precioMin=100000} y {@code precioMin=100000.00} comparten entrada.</p>
 *
 * <p><b>Invalidación:</b> explícita y en TODA escritura de inmuebles
 * ({@link GestionInmuebleService}: alta, edición y borrado lógico), siempre
 * DESPUÉS del commit ({@link TransactionSynchronization#afterCommit()}) para
 * que una lectura concurrente no re-cachee el total viejo entre la evicción y
 * el commit. El TTL corto ({@code manti.inmuebles.conteo-cache.ttl}, 2 min por
 * defecto) es la red de seguridad ante escrituras que no pasen por el servicio
 * (p.ej. un UPDATE directo en consola). El contrato de la API NO cambia:
 * {@code GET /api/v1/inmuebles} sigue devolviendo {@code totalElements} en la
 * primera página.</p>
 */
@Slf4j
@Component
public class ConteoInmueblesCache {

    private final InmuebleRepository repository;
    private final Cache<InmuebleFiltros, Long> cache;

    public ConteoInmueblesCache(InmuebleRepository repository,
                                @Value("${manti.inmuebles.conteo-cache.ttl:2m}") Duration ttl,
                                @Value("${manti.inmuebles.conteo-cache.max-entradas:2000}") long maxEntradas) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxEntradas)
                .recordStats()
                .build();
        log.info("Caché de conteo de inmuebles: TTL={}, max entradas={}", ttl, maxEntradas);
    }

    /**
     * Total de inmuebles que cumplen los filtros, sirviendo desde la caché si
     * la clave ya está (miss = COUNT a BD y cacheo del resultado).
     */
    public long contar(InmuebleFiltros filtros) {
        Long total = cache.get(filtros, clave -> repository.contar(clave));
        return total;
    }

    /**
     * Evicta todas las entradas tras el commit de la transacción activa (o de
     * inmediato si no hay transacción). Se invalida TODO el dominio de la
     * caché porque una escritura puede alterar el total de CUALQUIER
     * combinación de filtros; las escrituras de inmuebles son poco frecuentes
     * y la caché se repuebla sola en la siguiente lectura.
     */
    public void invalidarTrasCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.invalidateAll();
                }
            });
        } else {
            cache.invalidateAll();
        }
    }
}
