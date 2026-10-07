package es.manti.swipes.api.dto;

import es.manti.inmuebles.api.dto.InmuebleSummary;
import es.manti.swipes.application.ListadoSwipe;
import es.manti.swipes.application.SwipeResultado;
import es.manti.swipes.domain.DecisionSwipe;
import es.manti.swipes.domain.InmuebleSwipeResumen;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Mapeo proyección/resultados de dominio → DTO del módulo swipes. Las entidades
 * jamás salen de aquí: la respuesta sólo contiene records de este paquete (y el
 * {@code InmuebleSummary} público de {@code inmuebles}, api → api).
 *
 * <p>El paso {@code String → enum} de {@code tipo}/{@code estado} es el punto
 * donde el literal que devolvió el {@code CAST} del JPQL recupera su tipo de
 * dominio: es el ÚNICO sitio donde el módulo swipes mira hacia dentro de
 * {@code inmuebles}, y lo hace por su {@code api} pública.</p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SwipeMapper {

    /**
     * Literal del request → enum de dominio. Sólo se invoca DESPUÉS de que
     * {@code @Pattern} haya garantizado {@code LIKE|NOPE}, por lo que
     * {@code valueOf} no puede lanzar.
     */
    default DecisionSwipe toDecision(String valor) {
        return DecisionSwipe.valueOf(valor);
    }

    SwipeResponse toResponse(SwipeResultado resultado);

    InmuebleSummary toSummary(InmuebleSwipeResumen fila);

    List<InmuebleSummary> toSummaries(List<InmuebleSwipeResumen> filas);

    @Mapping(target = "content", source = "contenido")
    @Mapping(target = "nextCursor", source = "cursorSiguiente")
    SwipePage toPage(ListadoSwipe listado);
}
