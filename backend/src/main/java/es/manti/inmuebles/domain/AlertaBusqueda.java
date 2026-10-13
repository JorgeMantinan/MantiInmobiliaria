package es.manti.inmuebles.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Búsqueda guardada de un usuario (issue #96): los MISMOS filtros que el
 * listado público más un nombre y la marca de agua del último aviso.
 *
 * <p><b>Marca de agua</b> ({@code ultimo_inmueble_avisado_id}): el job sólo
 * considera inmuebles con {@code id} superior; así un inmueble que encaje pero
 * fue creado ANTES de guardar la alerta (o antes del último aviso) no dispara
 * un correo duplicado. Al crear la alerta se inicializa al mayor id existente
 * y sólo se avisa de lo que entre DESPUÉS.</p>
 *
 * <p>Los filtros viven en columnas tipadas (misma semántica que los query
 * params de {@code GET /api/v1/inmuebles}); el patrón de municipio se construye
 * al evaluar, en {@code application} — el dominio no depende de la aplicación.</p>
 */
@Entity
@Table(name = "alertas_busqueda")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertaBusqueda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 8)
    private EstadoInmueble estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 16)
    private TipoInmueble tipo;

    @Column(name = "municipio", length = 120)
    private String municipio;

    @Column(name = "precio_min", precision = 12, scale = 2)
    private BigDecimal precioMin;

    @Column(name = "precio_max", precision = 12, scale = 2)
    private BigDecimal precioMax;

    @Column(name = "habitaciones_min")
    private Short habitacionesMin;

    /** Filtro tri-estado: null = sin filtrar, true/false = sólo destacados/no. */
    @Column(name = "destacado")
    private Boolean destacado;

    @Column(name = "ultimo_inmueble_avisado_id", nullable = false)
    private Long ultimoInmuebleAvisadoId;

    @Column(name = "ultimo_aviso_en")
    private Instant ultimoAvisoEn;

    @Column(nullable = false)
    private boolean activa;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /**
     * Alta de una alerta ACTIVA cuya marca de agua apunta al mayor inmueble
     * existente: sólo se avisará de inmuebles nuevos a partir de ese punto.
     *
     * @param usuarioId              dueño de la alerta
     * @param datos                  nombre y filtros ya validados en la API
     * @param ultimoInmuebleExistente mayor id de inmuebles al guardar (marca)
     */
    public static AlertaBusqueda alta(Long usuarioId, AlertaDatos datos,
                                      Long ultimoInmuebleExistente) {
        AlertaBusqueda alerta = new AlertaBusqueda();
        alerta.usuarioId = usuarioId;
        alerta.nombre = datos.nombre();
        alerta.estado = datos.estado();
        alerta.tipo = datos.tipo();
        alerta.municipio = datos.municipio();
        alerta.precioMin = datos.precioMin();
        alerta.precioMax = datos.precioMax();
        alerta.habitacionesMin = datos.habitacionesMin();
        alerta.destacado = datos.destacado();
        alerta.ultimoInmuebleAvisadoId = ultimoInmuebleExistente;
        alerta.activa = true;
        return alerta;
    }

    /** Renombra la alerta (el nombre ya viene validado de la API). */
    public void renombrar(String nuevoNombre) {
        this.nombre = nuevoNombre;
    }

    /** Activa o pausa la alerta; pausada no se evalúa en el job. */
    public void cambiarActiva(boolean nuevaActividad) {
        this.activa = nuevaActividad;
    }

    /**
     * Registra un aviso emitido: avanza la marca de agua al mayor inmueble
     * incluido en el aviso y anota cuándo fue. Sólo el job llama a este método.
     */
    public void registrarAviso(Long inmuebleId, Instant cuando) {
        this.ultimoInmuebleAvisadoId = inmuebleId;
        this.ultimoAvisoEn = cuando;
    }
}
