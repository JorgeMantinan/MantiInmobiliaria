package es.manti.inmuebles.application;

import es.manti.inmuebles.domain.AlertaBusqueda;
import es.manti.inmuebles.domain.AlertaBusquedaRepository;
import es.manti.inmuebles.domain.AlertaDatos;
import es.manti.inmuebles.domain.AlertaLimiteExcedidoException;
import es.manti.inmuebles.domain.AlertaNoEncontradaException;
import es.manti.inmuebles.domain.InmuebleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD de las alertas de búsqueda del usuario autenticado (issue #96).
 *
 * <p><b>Owner-scoped</b>: toda operación recibe el {@code usuarioId} leído del
 * JWT en la capa api y las queries lo exigen, de modo que una alerta ajena es
 * siempre 404 (mismo criterio que los favoritos).</p>
 *
 * <p><b>Validación de filtros reutilizada</b>: el alta llama a
 * {@link BusquedaInmuebleService#validarRangos}, la MISMA rutina que valida los
 * query params del listado público ({@code precioMin > precioMax}, precios
 * negativos, habitaciones &lt; 0), de modo que una alerta guarda exactamente lo
 * que el listado aceptaría.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertaBusquedaService {

    /**
     * Techo de alertas por usuario (skill seguridad A04): el job evalúa TODAS
     * las activas en cada pasada, así que sin techo una cuenta podría encolar
     * un trabajo ilimitado con altas en bucle.
     */
    static final int MAXIMO_POR_USUARIO = 50;

    private final AlertaBusquedaRepository alertas;
    private final InmuebleRepository inmuebles;

    /** Alertas del usuario, de más reciente a más antigua. */
    @Transactional(readOnly = true)
    public List<AlertaBusqueda> listar(Long usuarioId) {
        return alertas.porUsuario(usuarioId);
    }

    /**
     * Alta de una alerta ACTIVA cuya marca de agua queda fijada al mayor id de
     * inmueble existente: sólo se avisará de lo que entre a partir de AHORA.
     */
    @Transactional
    public AlertaBusqueda crear(Long usuarioId, AlertaDatos datos) {
        BusquedaInmuebleService.validarRangos(busqueda(datos));
        if (alertas.cuantasPorUsuario(usuarioId) >= MAXIMO_POR_USUARIO) {
            throw new AlertaLimiteExcedidoException(MAXIMO_POR_USUARIO);
        }
        AlertaBusqueda creada = alertas.guardar(
                AlertaBusqueda.alta(usuarioId, datos, inmuebles.maximoId()));
        log.info("Alerta {} creada para el usuario {}", creada.getId(), usuarioId);
        return creada;
    }

    /**
     * Edición parcial de nombre y/o actividad; los campos nulos del comando se
     * interpretan como "sin cambios". 404 si la alerta no es del usuario.
     */
    @Transactional
    public AlertaBusqueda actualizar(Long usuarioId, Long id, String nombre, Boolean activa) {
        AlertaBusqueda alerta = porIdYUsuario(id, usuarioId);
        if (nombre != null) {
            alerta.renombrar(nombre);
        }
        if (activa != null) {
            alerta.cambiarActiva(activa);
        }
        return alertas.guardar(alerta);
    }

    /** Baja definitiva; sus avisos pendientes caen en cascada (FK V13). */
    @Transactional
    public void eliminar(Long usuarioId, Long id) {
        AlertaBusqueda alerta = porIdYUsuario(id, usuarioId);
        alertas.eliminar(alerta);
        log.info("Alerta {} eliminada del usuario {}", id, usuarioId);
    }

    /** Owner-scoped: id inexistente o ajeno → 404 (nunca 403, nunca 500). */
    private AlertaBusqueda porIdYUsuario(Long id, Long usuarioId) {
        return alertas.porIdYUsuario(id, usuarioId)
                .orElseThrow(() -> new AlertaNoEncontradaException(id));
    }

    /**
     * Los datos de la alerta como la MISMA entrada que valida el listado: la
     * validación de rangos vive en un solo sitio y evita que los dos caminos
     * (query params y alerta) diverjan.
     */
    private static BusquedaInmueblesCommand busqueda(AlertaDatos datos) {
        return new BusquedaInmueblesCommand(
                datos.estado(), datos.tipo(), datos.municipio(),
                datos.precioMin(), datos.precioMax(), datos.habitacionesMin(),
                datos.destacado(), null, null, BusquedaInmuebleService.LIMITE_MAXIMO);
    }
}
