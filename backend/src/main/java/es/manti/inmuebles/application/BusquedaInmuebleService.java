package es.manti.inmuebles.application;

import es.manti.inmuebles.domain.Inmueble;
import es.manti.inmuebles.domain.InmuebleCursor;
import es.manti.inmuebles.domain.InmuebleFiltros;
import es.manti.inmuebles.domain.InmuebleNoEncontradoException;
import es.manti.inmuebles.domain.InmuebleOrden;
import es.manti.inmuebles.domain.InmuebleRepository;
import es.manti.inmuebles.domain.InmuebleResumen;
import es.manti.inmuebles.domain.ParametroInvalidoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Casos de uso de LECTURA pública de inmuebles: listado con filtros y
 * paginación por cursor (keyset) y detalle. Sin N+1: el listado va sobre
 * proyecciones ligeras ({@link InmuebleResumen}) y no toca colecciones ni
 * usuarios.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusquedaInmuebleService {

    static final int LIMITE_MAXIMO = 50;

    private final InmuebleRepository repository;

    /**
     * Página del listado público. Orden por defecto {@code created_at DESC, id DESC};
     * cursor opaco base64 ({@link InmuebleCursor#codificar()}) y {@code limit}
     * acotado a {@code 1..50}.
     */
    @Transactional(readOnly = true)
    public ListadoInmuebles listar(BusquedaInmueblesCommand comando) {
        validarRangos(comando);
        InmuebleOrden orden = InmuebleOrden.desdeParametro(comando.sort());
        int limite = Math.clamp(comando.limit(), 1, LIMITE_MAXIMO);
        InmuebleCursor cursor = comando.cursor() == null || comando.cursor().isBlank()
                ? null
                : InmuebleCursor.decodificar(comando.cursor(), orden);
        InmuebleFiltros filtros = new InmuebleFiltros(
                comando.estado(),
                comando.tipo(),
                patronMunicipio(comando.municipio()),
                comando.precioMin(),
                comando.precioMax(),
                comando.habitacionesMin(),
                comando.destacado());

        // Se pide una fila extra para saber si queda página siguiente sin COUNT.
        List<InmuebleResumen> candidatas = repository.listar(filtros, orden, cursor, limite + 1);
        boolean hayMasPaginas = candidatas.size() > limite;
        List<InmuebleResumen> contenido = hayMasPaginas
                ? List.copyOf(candidatas.subList(0, limite))
                : candidatas;
        String cursorSiguiente = hayMasPaginas
                ? codificarCursor(orden, contenido.get(contenido.size() - 1))
                : null;
        Long totalElements = cursor == null ? repository.contar(filtros) : null;
        log.debug("Listado de inmuebles: {} filas, orden {}, hayMas={}",
                contenido.size(), orden, hayMasPaginas);
        return new ListadoInmuebles(contenido, cursorSiguiente, totalElements);
    }

    /** Detalle completo; 404 si no existe o si está eliminado. */
    @Transactional(readOnly = true)
    public Inmueble detalle(Long id) {
        return repository.porId(id).orElseThrow(() -> new InmuebleNoEncontradoException(id));
    }

    /**
     * Rangos de filtro incoherentes → 400 con {@code errors:[{field, message}]}.
     * No se puede expresar con anotaciones porque compara DOS parámetros
     * ({@code precioMin > precioMax}); por eso se valida en el caso de uso,
     * antes de tocar la base de datos.
     */
    static void validarRangos(BusquedaInmueblesCommand comando) {
        if (comando.precioMin() != null && comando.precioMin().signum() < 0) {
            throw new ParametroInvalidoException("precioMin", "no puede ser negativo");
        }
        if (comando.precioMax() != null && comando.precioMax().signum() < 0) {
            throw new ParametroInvalidoException("precioMax", "no puede ser negativo");
        }
        if (comando.precioMin() != null && comando.precioMax() != null
                && comando.precioMin().compareTo(comando.precioMax()) > 0) {
            throw new ParametroInvalidoException("precioMin", "no puede ser mayor que precioMax");
        }
        if (comando.habitacionesMin() != null && comando.habitacionesMin() < 0) {
            throw new ParametroInvalidoException("habitacionesMin", "no puede ser negativo");
        }
    }

    /**
     * Patrón para {@code LOWER(municipio) LIKE ? ESCAPE '!'}: recorta, escapa los
     * comodines del LIKE y envuelve en {@code %}; el plegado de mayúsculas se
     * hace AQUÍ ({@code toLowerCase}) y no con {@code LOWER(:p)} en la query:
     * Hibernate no infiere el tipo JDBC de un parámetro dentro de una función y
     * lo bindea como {@code JAVA_OBJECT} (null → bytea en pgjdbc), lo que
     * rompía el listado sin filtros con {@code function lower(bytea) does not
     * exist}. El escape va antes porque {@code toLowerCase} no altera {@code '!'}.
     */
    static String patronMunicipio(String municipio) {
        if (municipio == null || municipio.isBlank()) {
            return null;
        }
        String limpio = municipio.trim();
        String escapado = limpio.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escapado.toLowerCase(Locale.ROOT) + "%";
    }

    private static String codificarCursor(InmuebleOrden orden, InmuebleResumen ultima) {
        return orden.porPrecio()
                ? new InmuebleCursor(orden, null, ultima.precio(), ultima.id()).codificar()
                : new InmuebleCursor(orden, ultima.createdAt(), null, ultima.id()).codificar();
    }
}
