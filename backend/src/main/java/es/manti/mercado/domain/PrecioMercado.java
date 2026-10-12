package es.manti.mercado.domain;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Precio oficial del mercado publicado por el MIVAU. Datos EXTERNOS: la fila
 * sólo se crea/modifica desde el importador (upsert por clave natural, migración
 * V10), nunca desde la API de inmuebles.
 *
 * <p>Sin setters: es un modelo de lectura. La identidad de negocio es
 * {@code (codigo_ine, tipo, indicador, tipologia, anio, trimestre)} — la UNIQUE
 * {@code uk_precios_mercado_clave} — y la conserva la BD.</p>
 */
@Entity
@Table(name = "precios_mercado")
@Getter
@NoArgsConstructor
public class PrecioMercado {

    /** Unidad de los precios anuales del alquiler municipal (dato anual). */
    public static final short TRIMESTRE_ANUAL = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_ine", nullable = false, length = 5)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String codigoIne;

    @Enumerated(EnumType.STRING)
    @Column(name = "ambito", nullable = false, length = 16)
    private AmbitoMercado ambito;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 8)
    private TipoPrecio tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicador", nullable = false, length = 20)
    private IndicadorPrecio indicador;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipologia", nullable = false, length = 16)
    private TipologiaVivienda tipologia;

    @Column(name = "anio", nullable = false)
    private Short anio;

    /** 0 = anual (alquiler municipal); 1..4 = trimestre (valor tasado). */
    @Column(name = "trimestre", nullable = false)
    private Short trimestre;

    @Column(name = "valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad", nullable = false, length = 16)
    private UnidadPrecio unidad;

    /** Nº de testigos SERPAVI; null cuando la fuente no lo publica (venta). */
    @Column(name = "n_testigos")
    private Integer nTestigos;

    /** Nombre de la provincia; sólo en ámbito PROVINCIA (CHECK de la V10). */
    @Column(name = "provincia", length = 100)
    private String provincia;

    @Column(name = "fuente", nullable = false, length = 100)
    private String fuente;

    @Column(name = "fecha_descarga", nullable = false)
    private Instant fechaDescarga;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
