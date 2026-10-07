package es.manti.swipes.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Fila ligera del deck y de favoritos (constructor expression de los
 * {@code @Query} del repositorio JPA). Es la <b>proyección de listado</b> de
 * este módulo: nunca se hidrata la entidad {@link Swipe} ni la entidad de
 * inmueble para pintar 20 tarjetas.
 *
 * <p>Los campos {@code tipo} y {@code estado} viajan como {@link String} y no
 * como los enums del módulo {@code inmuebles}: {@code swipes} no importa
 * {@code es.manti.inmuebles.domain.*} (regla ArchUnit). El {@code CAST} ocurre
 * dentro del JPQL y la conversión a enum la hace el mapper del módulo, en la
 * capa {@code api}, que sí puede ver {@code InmuebleSummary}.</p>
 *
 * @param id              identificador del inmueble
 * @param titulo          título del anuncio
 * @param precio          precio en euros (numeric)
 * @param tipo            tipo de inmueble (literal, p. ej. {@code PISO})
 * @param estado          estado comercial (literal, {@code VENTA}/{@code ALQUILER})
 * @param superficieM2    superficie en m² (null si no consta)
 * @param habitaciones    nº de habitaciones (null si no consta)
 * @param banios          nº de baños (null si no consta)
 * @param municipio       municipio
 * @param provincia       provincia
 * @param imagenUrl       imagen principal (null si no consta)
 * @param destacado       marca de destacado
 * @param lat             latitud WGS84
 * @param lng             longitud WGS84
 * @param createdAt       fecha de alta del <b>inmueble</b> (campo del resumen)
 * @param cursorCreadoEn  clave temporal del cursor keyset: {@code created_at}
 *                        del inmueble (deck) o {@code creado_en} del swipe
 *                        (favoritos). Siempre presente para que la paginación
 *                        tenga un único helper, sin filas a medias.
 * @param cursorId        desempate del cursor keyset: id del inmueble (deck) o
 *                        id del swipe (favoritos)
 */
public record InmuebleSwipeResumen(
        Long id,
        String titulo,
        BigDecimal precio,
        String tipo,
        String estado,
        Integer superficieM2,
        Short habitaciones,
        Short banios,
        String municipio,
        String provincia,
        String imagenUrl,
        boolean destacado,
        double lat,
        double lng,
        Instant createdAt,
        Instant cursorCreadoEn,
        Long cursorId) {
}
