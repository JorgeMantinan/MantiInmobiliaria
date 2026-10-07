package es.manti.inmuebles.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Anuncio de inmueble. Soft-delete vía {@code eliminado_en}: las queries
 * públicas filtran siempre {@code eliminado_en IS NULL} (índices parciales de la
 * migración V2) y el histórico nunca se destruye.
 */
@Entity
@Table(name = "inmuebles")
@Getter
@Setter
@NoArgsConstructor
public class Inmueble {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Column(name = "precio", nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Column(name = "superficie_m2")
    private Integer superficieM2;

    @Column(name = "habitaciones", columnDefinition = "smallint")
    private Short habitaciones;

    @Column(name = "banios", columnDefinition = "smallint")
    private Short banios;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 16)
    private TipoInmueble tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 8)
    private EstadoInmueble estado;

    @Column(name = "direccion", nullable = false, columnDefinition = "text")
    private String direccion;

    @Column(name = "codigo_postal", length = 5)
    private String codigoPostal;

    @Column(name = "municipio", nullable = false, columnDefinition = "text")
    private String municipio;

    @Column(name = "provincia", nullable = false, columnDefinition = "text")
    private String provincia;

    @Column(name = "lat", nullable = false, columnDefinition = "double precision")
    private double lat;

    @Column(name = "lng", nullable = false, columnDefinition = "double precision")
    private double lng;

    /** Imagen principal; puede ser null (el front usa un placeholder). */
    @Column(name = "imagen_url", columnDefinition = "text")
    private String imagenUrl;

    /** Marca de curated/home. Sólo la toca el rol ADMIN. */
    @Column(name = "destacado", nullable = false)
    private boolean destacado;

    /** Soft-delete: null mientras el anuncio esté vigente. */
    @Column(name = "eliminado_en")
    private Instant eliminadoEn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Factory de alta: aplica los datos de negocio y deja el resto en su default. */
    public static Inmueble nuevo(InmuebleDatos datos) {
        Inmueble inmueble = new Inmueble();
        inmueble.aplicar(datos);
        return inmueble;
    }

    /** Actualización total (PUT): reemplaza los datos de negocio, no la identidad. */
    public void actualizar(InmuebleDatos datos) {
        aplicar(datos);
    }

    /** Soft-delete idempotente: no repite marca si ya estaba eliminado. */
    public void eliminar() {
        if (eliminadoEn == null) {
            eliminadoEn = Instant.now();
        }
    }

    private void aplicar(InmuebleDatos datos) {
        this.titulo = datos.titulo();
        this.descripcion = datos.descripcion();
        this.precio = datos.precio();
        this.superficieM2 = datos.superficieM2();
        this.habitaciones = datos.habitaciones();
        this.banios = datos.banios();
        this.tipo = datos.tipo();
        this.estado = datos.estado();
        this.direccion = datos.direccion();
        this.codigoPostal = datos.codigoPostal();
        this.municipio = datos.municipio();
        this.provincia = datos.provincia();
        this.lat = datos.lat();
        this.lng = datos.lng();
        this.imagenUrl = datos.imagenUrl();
        // destacado opcional en el request: null = no tocar / false en el alta
        if (datos.destacado() != null) {
            this.destacado = datos.destacado();
        }
    }
}
