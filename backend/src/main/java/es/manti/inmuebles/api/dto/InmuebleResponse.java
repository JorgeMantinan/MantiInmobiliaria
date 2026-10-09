package es.manti.inmuebles.api.dto;

import es.manti.inmuebles.domain.EstadoInmueble;
import es.manti.inmuebles.domain.TipoInmueble;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/** Detalle completo de un inmueble (GET /api/v1/inmuebles/{id}). */
@Schema(description = "Detalle completo de un inmueble")
public record InmuebleResponse(
        @Schema(description = "Identificador", example = "1")
        Long id,

        @Schema(description = "Título del anuncio", example = "Piso céntrico con ascensor")
        String titulo,

        @Schema(description = "Descripción completa", example = "Luminoso piso reformado...")
        String descripcion,

        @Schema(description = "Precio en euros", example = "245000.00")
        BigDecimal precio,

        @Schema(description = "Superficie en m²", example = "96")
        Integer superficieM2,

        @Schema(description = "Habitaciones", example = "3")
        Short habitaciones,

        @Schema(description = "Baños", example = "1")
        Short banios,

        @Schema(description = "Tipo de inmueble", example = "PISO")
        TipoInmueble tipo,

        @Schema(description = "Estado comercial", example = "VENTA")
        EstadoInmueble estado,

        @Schema(description = "Dirección completa", example = "Calle Mayor 1, 3º B")
        String direccion,

        @Schema(description = "Código postal español (5 dígitos)", example = "28013")
        String codigoPostal,

        @Schema(description = "Municipio", example = "Madrid")
        String municipio,

        @Schema(description = "Provincia", example = "Madrid")
        String provincia,

        @Schema(description = "Latitud WGS84", example = "40.4168")
        double lat,

        @Schema(description = "Longitud WGS84", example = "-3.7038")
        double lng,

        @Schema(description = "Imagen principal (URL https)", example = "https://picsum.photos/seed/inm1/800/600")
        String imagenUrl,

        @Schema(description = "Destacado en la portada", example = "false")
        boolean destacado,

        @Schema(description = "Fecha de alta (ISO-8601)", example = "2026-09-01T10:00:00Z")
        Instant createdAt,

        @Schema(description = "Última modificación (ISO-8601)", example = "2026-09-01T10:00:00Z")
        Instant updatedAt) {
}
