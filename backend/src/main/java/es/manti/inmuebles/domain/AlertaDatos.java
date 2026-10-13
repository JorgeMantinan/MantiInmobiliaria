package es.manti.inmuebles.domain;

import java.math.BigDecimal;

/**
 * Datos de alta de una alerta: nombre + los MISMOS campos de filtro que acepta
 * {@code GET /api/v1/inmuebles} (el contrato a guardar, issue #96).
 *
 * <p>Lo construye el mapper de la capa {@code api} a partir del request; los
 * rangos inválidos ({@code precioMin > precioMax}, negativos) se rechazan en
 * el caso de uso reutilizando la validación del listado público, antes de
 * tocar la base de datos.</p>
 *
 * @param nombre nombre propio de la alerta (1..80, validado en la API)
 */
public record AlertaDatos(
        String nombre,
        EstadoInmueble estado,
        TipoInmueble tipo,
        String municipio,
        BigDecimal precioMin,
        BigDecimal precioMax,
        Short habitacionesMin,
        Boolean destacado) {
}
