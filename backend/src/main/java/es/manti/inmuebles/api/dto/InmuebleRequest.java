package es.manti.inmuebles.api.dto;

import es.manti.inmuebles.domain.EstadoInmueble;
import es.manti.inmuebles.domain.TipoInmueble;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Alta (POST) y actualización total (PUT) de un inmueble. Un único payload para
 * ambos verbos porque el recurso es el mismo a nivel de campos y validación
 * (PUT = representación completa del recurso); request y response siguen siendo
 * tipos separados (regla 3 de la skill dto).
 *
 * <p>Rangos: coordenadas de España aproximadas (península, Baleares, Canarias y
 * Ceuta/Melilla), código postal de 5 dígitos e imagen sólo https.</p>
 */
@Schema(description = "Datos de alta/actualización de un inmueble")
public record InmuebleRequest(
        @NotBlank @Size(max = 200)
        @Schema(description = "Título del anuncio", example = "Piso céntrico con ascensor",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
        String titulo,

        @Size(max = 5000)
        @Schema(description = "Descripción larga (opcional)", example = "Luminoso piso reformado...")
        String descripcion,

        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
        @Schema(description = "Precio en euros; debe ser mayor que 0",
                example = "245000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal precio,

        @Positive
        @Schema(description = "Superficie en m² (opcional)", example = "96")
        Integer superficieM2,

        @Min(0) @Max(200)
        @Schema(description = "Habitaciones (opcional)", example = "3")
        Short habitaciones,

        @Min(0) @Max(50)
        @Schema(description = "Baños (opcional)", example = "1")
        Short banios,

        @NotNull
        @Schema(description = "Tipo de inmueble", example = "PISO",
                requiredMode = Schema.RequiredMode.REQUIRED)
        TipoInmueble tipo,

        @NotNull
        @Schema(description = "Estado comercial", example = "VENTA",
                requiredMode = Schema.RequiredMode.REQUIRED)
        EstadoInmueble estado,

        @NotBlank @Size(max = 500)
        @Schema(description = "Dirección completa", example = "Calle Mayor 1, 3º B",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String direccion,

        @Pattern(regexp = "^[0-9]{5}$")
        @Schema(description = "Código postal español (5 dígitos, opcional)", example = "28013")
        String codigoPostal,

        @NotBlank @Size(max = 120)
        @Schema(description = "Municipio", example = "Madrid", requiredMode = Schema.RequiredMode.REQUIRED)
        String municipio,

        @NotBlank @Size(max = 120)
        @Schema(description = "Provincia", example = "Madrid", requiredMode = Schema.RequiredMode.REQUIRED)
        String provincia,

        @NotNull @DecimalMin("27.0") @DecimalMax("44.0")
        @Schema(description = "Latitud WGS84 (España: 27..44)", example = "40.4168",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Double lat,

        @NotNull @DecimalMin("-19.0") @DecimalMax("5.0")
        @Schema(description = "Longitud WGS84 (España: -19..5)", example = "-3.7038",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Double lng,

        // Sólo URL https con caracteres de URL RFC 3986: sin comillas ni < >,
        // para que no se cuelen payloads de inyección en el atributo del front.
        @Size(max = 500) @Pattern(regexp = "^https://[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+$")
        @Schema(description = "Imagen principal; sólo URL https con caracteres de URL (opcional)",
                example = "https://picsum.photos/seed/inm1/800/600")
        String imagenUrl,

        @Schema(description = "Marca de destacado; sólo ADMIN (opcional, por defecto false)",
                example = "false")
        Boolean destacado) {
}
