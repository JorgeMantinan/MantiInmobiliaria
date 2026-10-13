package es.manti.inmuebles.application;

import es.manti.inmuebles.domain.AlertaBusquedaRepository;
import es.manti.inmuebles.domain.InmuebleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.OptionalLong;

/**
 * Barrido de las alertas activas (issue #96, endurecido en el follow-up #98):
 * pagina las activas en lotes cortos y delega cada lote en
 * {@link AlertaLoteService#evaluarLote}, que corre en SU propia transacción.
 *
 * <p><b>Cortocircuito</b>: si el mayor id del catálogo no supera la marca de
 * agua MÍNIMA entre las alertas activas, NINGUNA alerta tiene nada pendiente y
 * la pasada sale sin tocar nada (dos SELECT baratos y cero escrituras). Se usa
 * la MENOR marca —no la mayor— porque una alerta con marca reciente no debe
 * tapar a otra con marca antigua y pendientes.</p>
 *
 * <p><b>Evidencia de las queries</b> (reutilizadas del issue #96, ya medidas
 * con EXPLAIN en el PR #97): {@code listarNuevosDesde} es un range scan por
 * la PK ({@code i.id > :desdeId ORDER BY i.id ASC LIMIT n}, sin Sort ni
 * OFFSET); {@code minimaMarcaActiva} recorre sólo el índice parcial
 * {@code idx_alertas_busqueda_activas (id) WHERE activa}; el lote
 * {@code activasDesde} usa el mismo índice y se bloquea
 * {@code FOR UPDATE SKIP LOCKED} (multi-instancia).</p>
 *
 * <p>Esta clase NO lleva {@code @Transactional}: sólo orquesta. La
 * transaccionalidad vive en cada lote (corto) y cada lote es independiente —
 * un fallo en uno no deshace los avisos ya encolados en los anteriores.</p>
 */
@Slf4j
@Service
public class AlertaEvaluacionService {

    private final AlertaBusquedaRepository alertas;
    private final InmuebleRepository inmuebles;
    private final AlertaLoteService loteService;
    private final int tamanoLote;

    public AlertaEvaluacionService(AlertaBusquedaRepository alertas,
                                   InmuebleRepository inmuebles,
                                   AlertaLoteService loteService,
                                   @Value("${manti.alertas.lote:500}") int tamanoLote) {
        this.alertas = alertas;
        this.inmuebles = inmuebles;
        this.loteService = loteService;
        this.tamanoLote = tamanoLote;
    }

    /**
     * Evalúa TODAS las alertas activas, en lotes con transacción propia, y
     * devuelve el número de alertas avisadas en esta pasada.
     */
    public int evaluar() {
        OptionalLong menorMarca = alertas.minimaMarcaActiva();
        if (menorMarca.isEmpty()) {
            // Ni una alerta activa: no hay nada que barrer.
            return 0;
        }
        if (inmuebles.maximoId() <= menorMarca.getAsLong()) {
            // Cortocircuito: no hay inmuebles por encima de la marca más
            // antigua — ninguna alerta tiene nada pendiente.
            return 0;
        }
        int avisadas = 0;
        long desdeId = 0;
        while (true) {
            AlertaLoteService.Resultado lote = loteService.evaluarLote(desdeId, tamanoLote);
            if (!lote.hayMas()) {
                break;
            }
            avisadas += lote.avisadas();
            desdeId = lote.ultimaAlertaId();
        }
        if (avisadas > 0) {
            log.info("Alertas de búsqueda con avisos encolados: {}", avisadas);
        }
        return avisadas;
    }
}
