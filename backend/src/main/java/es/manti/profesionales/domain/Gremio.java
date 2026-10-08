package es.manti.profesionales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Gremio (oficio) del catálogo cerrado: {@code fontanero}, {@code albañil}...
 * Los slugs son el identificador que la API expone y que el frontend envía en
 * {@code gremios[]}; la semilla vive en {@code V4__profesionales.sql}.
 *
 * <p>Tabla de consulta sin historial: por eso no lleva columnas de auditoría.</p>
 */
@Entity
@Table(name = "gremios")
@Getter
@NoArgsConstructor
public class Gremio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", nullable = false, length = 40)
    private String slug;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    /**
     * Forma canónica de un slug para compararlo: sin acentos ni mayúsculas, de
     * modo que {@code albañil} y {@code albanil} resuelvan el mismo gremio.
     * La usan tanto el servicio (qué slug falta) como el adaptador (qué filas
     * coinciden), así que la comparación es SIEMPRE la misma en ambas capas.
     */
    public static String normalizarSlug(String slug) {
        if (slug == null) {
            return "";
        }
        return Normalizer.normalize(slug.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }
}
