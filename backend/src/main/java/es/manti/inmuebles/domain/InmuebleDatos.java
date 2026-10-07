package es.manti.inmuebles.domain;

import java.math.BigDecimal;

/**
 * Datos de negocio de un inmueble (alta y actualización). Vive en {@code domain}
 * porque es la entrada de los métodos {@link Inmueble#nuevo(InmuebleDatos)} y
 * {@link Inmueble#actualizar(InmuebleDatos)}: la capa application no construye
 * entidades y la capa api no conoce la entidad.
 *
 * @param destacado null = no modificar (PUT) / false en el alta
 */
public record InmuebleDatos(
        String titulo,
        String descripcion,
        BigDecimal precio,
        Integer superficieM2,
        Short habitaciones,
        Short banios,
        TipoInmueble tipo,
        EstadoInmueble estado,
        String direccion,
        String codigoPostal,
        String municipio,
        String provincia,
        double lat,
        double lng,
        String imagenUrl,
        Boolean destacado) {
}
