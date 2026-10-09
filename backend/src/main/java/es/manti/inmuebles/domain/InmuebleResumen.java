package es.manti.inmuebles.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Proyección ligera del listado (constructor expression en los {@code @Query} del
 * repositorio JPA). NO incluye {@code descripcion} (texto largo) ni
 * {@code direccion}: el resumen de tarjeta no los necesita y el detalle completo
 * se sirve aparte con una query de una sola fila.
 *
 * @param id          identificador
 * @param titulo      título del anuncio
 * @param precio      precio en euros (numeric)
 * @param tipo        tipo de inmueble
 * @param estado      VENTA o ALQUILER
 * @param superficieM2 superficie en m² (null si no consta)
 * @param habitaciones nº de habitaciones (null si no consta)
 * @param banios      nº de baños (null si no consta)
 * @param municipio   municipio
 * @param provincia   provincia
 * @param imagenUrl   imagen principal (null si no consta)
 * @param destacado   marca de destacado
 * @param lat         latitud WGS84
 * @param lng         longitud WGS84
 * @param createdAt   instante de alta (clave del cursor por defecto)
 */
public record InmuebleResumen(
        Long id,
        String titulo,
        BigDecimal precio,
        TipoInmueble tipo,
        EstadoInmueble estado,
        Integer superficieM2,
        Short habitaciones,
        Short banios,
        String municipio,
        String provincia,
        String imagenUrl,
        boolean destacado,
        double lat,
        double lng,
        Instant createdAt) {
}
