package es.manti.profesionales.domain;

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

import java.time.Instant;

/**
 * Fila del <b>outbox transaccional</b> (skill microservicios): se escribe en la
 * MISMA transacción que la {@link Solicitud} y un despachador {@code @Scheduled}
 * la envía después, fuera de la transacción. Así un fallo de Brevo/Telegram o
 * un reinicio del proceso nunca dejan la notificación perdida ni bloquean el
 * alta de la solicitud.
 *
 * <p>La fila es AUTÓNOMA: sólo guarda {@code solicitud_id}/{@code destinatario_id}
 * como claves foráneas, sin asociaciones JPA, para poder leerla y marcarla en
 * transacciones propias sin arrastrar entidades perezosas.</p>
 *
 * <p><b>Origen dual (issue #96)</b>: desde V13 la fila también puede venir de
 * una ALERTA DE BÚSQUEDA — entonces {@code solicitud_id} es NULL y la columna
 * {@code alerta_id} (NO mapeada aquí: el despachador no la necesita) lleva la
 * referencia, con el CHECK {@code ck_notificaciones_outbox_origen} garantizando
 * exactamente UN origen por fila. Por eso {@code solicitud_id} ya no es
 * {@code nullable = false}: el despachador carga las filas de alertas al mismo
 * lote que las de solicitudes y su destino/canal/cuerpo son autosuficientes.</p>
 */
@Entity
@Table(name = "notificaciones_outbox")
@Getter
@NoArgsConstructor
public class NotificacionOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NULL sólo en las filas de alertas de búsqueda (ver javadoc de la clase). */
    @Column(name = "solicitud_id")
    private Long solicitudId;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 10)
    private CanalNotificacion canal;

    @Enumerated(EnumType.STRING)
    @Column(name = "destinatario_tipo", nullable = false, length = 15)
    private DestinatarioNotificacion destinatarioTipo;

    @Column(name = "destinatario_id")
    private Long destinatarioId;

    /** Email del profesional o {@code chat_id} del grupo de Telegram. */
    @Column(name = "destino", nullable = false, length = 255)
    private String destino;

    @Column(name = "asunto", nullable = false, length = 200)
    private String asunto;

    @Column(name = "cuerpo", nullable = false, columnDefinition = "text")
    private String cuerpo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoNotificacion estado;

    @Column(name = "intentos", nullable = false)
    private Short intentos;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "enviado_en")
    private Instant enviadoEn;

    /** Email personalizado para UN profesional ACTIVO de la solicitud. */
    public static NotificacionOutbox email(Solicitud solicitud, Profesional profesional,
                                           String asunto, String cuerpo) {
        NotificacionOutbox fila = base(solicitud, CanalNotificacion.EMAIL, asunto, cuerpo);
        fila.destinatarioTipo = DestinatarioNotificacion.PROFESIONAL;
        fila.destinatarioId = profesional.getId();
        fila.destino = profesional.getEmail();
        return fila;
    }

    /** Aviso al GRUPO de Telegram (una fila por gremio pedido). */
    public static NotificacionOutbox telegram(Solicitud solicitud, String chatId,
                                              String asunto, String cuerpo) {
        NotificacionOutbox fila = base(solicitud, CanalNotificacion.TELEGRAM, asunto, cuerpo);
        fila.destinatarioTipo = DestinatarioNotificacion.GRUPO;
        fila.destinatarioId = null;
        fila.destino = chatId;
        return fila;
    }

    private static NotificacionOutbox base(Solicitud solicitud, CanalNotificacion canal,
                                           String asunto, String cuerpo) {
        NotificacionOutbox fila = new NotificacionOutbox();
        fila.solicitudId = solicitud.getId();
        fila.canal = canal;
        fila.asunto = asunto;
        fila.cuerpo = cuerpo;
        fila.estado = EstadoNotificacion.PENDIENTE;
        fila.intentos = 0;
        return fila;
    }
}
