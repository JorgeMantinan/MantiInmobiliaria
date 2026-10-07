package es.manti.inmuebles.infra.adapter;

import es.manti.inmuebles.domain.EstadoInmueble;
import es.manti.inmuebles.domain.Inmueble;
import es.manti.inmuebles.domain.InmuebleCursor;
import es.manti.inmuebles.domain.InmuebleFiltros;
import es.manti.inmuebles.domain.InmuebleOrden;
import es.manti.inmuebles.domain.InmuebleRepository;
import es.manti.inmuebles.domain.InmuebleResumen;
import es.manti.inmuebles.domain.TipoInmueble;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador JPA del puerto {@link InmuebleRepository}. Ramifica entre la query
 * "sin cursor" (primera página: la B-tree entrega filas ya ordenadas) y la
 * "con cursor" (keyset con Index Cond); ver el javadoc de
 * {@link InmuebleJpaRepository} por qué son métodos distintos.
 */
@Repository
@RequiredArgsConstructor
public class InmuebleRepositoryAdapter implements InmuebleRepository {

    private final InmuebleJpaRepository jpaRepository;

    @Override
    public List<InmuebleResumen> listar(InmuebleFiltros filtros, InmuebleOrden orden,
                                        InmuebleCursor cursor, int limite) {
        // page 0 + límite: el keyset va DENTRO de la query (predicado de cursor),
        // la Pageable sólo aporta el LIMIT — jamás un OFFSET profundo.
        Pageable pagina = PageRequest.of(0, limite);
        EstadoInmueble estado = filtros.estado();
        TipoInmueble tipo = filtros.tipo();
        String municipio = filtros.municipioPatron();
        BigDecimal precioMin = filtros.precioMin();
        BigDecimal precioMax = filtros.precioMax();
        Short habitacionesMin = filtros.habitacionesMin();
        Boolean destacado = filtros.destacado();

        if (cursor == null) {
            return switch (orden) {
                case CREADO_DESC -> jpaRepository.listarPorCreadoDesc(
                        estado, tipo, municipio, precioMin, precioMax, habitacionesMin, destacado, pagina);
                case PRECIO_ASC -> jpaRepository.listarPorPrecioAsc(
                        estado, tipo, municipio, precioMin, precioMax, habitacionesMin, destacado, pagina);
                case PRECIO_DESC -> jpaRepository.listarPorPrecioDesc(
                        estado, tipo, municipio, precioMin, precioMax, habitacionesMin, destacado, pagina);
            };
        }
        return switch (orden) {
            case CREADO_DESC -> jpaRepository.listarPorCreadoDescConCursor(
                    estado, tipo, municipio, precioMin, precioMax, habitacionesMin, destacado,
                    cursor.creadoEn(), cursor.id(), pagina);
            case PRECIO_ASC -> jpaRepository.listarPorPrecioAscConCursor(
                    estado, tipo, municipio, precioMin, precioMax, habitacionesMin, destacado,
                    cursor.precio(), cursor.id(), pagina);
            case PRECIO_DESC -> jpaRepository.listarPorPrecioDescConCursor(
                    estado, tipo, municipio, precioMin, precioMax, habitacionesMin, destacado,
                    cursor.precio(), cursor.id(), pagina);
        };
    }

    @Override
    public long contar(InmuebleFiltros filtros) {
        return jpaRepository.contar(
                filtros.estado(), filtros.tipo(), filtros.municipioPatron(),
                filtros.precioMin(), filtros.precioMax(), filtros.habitacionesMin(), filtros.destacado());
    }

    @Override
    public Optional<Inmueble> porId(Long id) {
        return jpaRepository.findByIdAndEliminadoEnIsNull(id);
    }

    @Override
    public Inmueble guardar(Inmueble inmueble) {
        return jpaRepository.save(inmueble);
    }
}
