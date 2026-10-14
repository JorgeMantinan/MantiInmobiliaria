package es.manti.inmuebles.infra.adapter;

import es.manti.inmuebles.domain.AlertaNotificacionRepository;
import es.manti.inmuebles.domain.CanalAvisoAlerta;
import es.manti.inmuebles.domain.FilaAvisoAlerta;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador del puerto {@link AlertaNotificacionRepository}.
 *
 * <p><b>Esquema compartido, sin imports de otros módulos</b> (mismo criterio que
 * el deck de swipes sobre {@code inmuebles}): se escribe directamente en la
 * tabla {@code notificaciones_outbox} del módulo profesionales con SQL NATIVO y
 * columna de origen {@code alerta_id}; el despachador existente (Brevo +
 * Telegram) recoge esas filas por su estado {@code PENDIENTE} sin distinguir el
 * origen. El destinatario EMAIL usa {@code destinatario_tipo = 'USUARIO'} (sin
 * {@code destinatario_id}: la FK de esa columna sólo cubre profesionales) y el
 * de TELEGRAM {@code 'GRUPO'}, igual que los avisos de solicitudes.</p>
 *
 * <p>La lectura de los emails de los usuarios es también nativa sobre
 * {@code usuarios}: la entidad ajena jamás se importa. El {@code ANY(:ids)}
 * resuelve TODOS los destinatarios del lote en UNA query (follow-up #98).</p>
 */
@Repository
@RequiredArgsConstructor
public class AlertaNotificacionAdapter implements AlertaNotificacionRepository {

    private final EntityManager entityManager;

    @Override
    public Map<Long, String> emailsDeUsuarios(Collection<Long> usuarioIds) {
        if (usuarioIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> filas = entityManager
                .createNativeQuery(
                        "SELECT id, email FROM usuarios WHERE id = ANY(:ids) AND activo = true",
                        Object[].class)
                .setParameter("ids", usuarioIds.toArray(new Long[0]))
                .getResultList();
        Map<Long, String> emails = new HashMap<>(filas.size());
        for (Object[] fila : filas) {
            emails.put(((Number) fila[0]).longValue(), (String) fila[1]);
        }
        return emails;
    }

    @Override
    public void encolar(FilaAvisoAlerta fila) {
        entityManager.createNativeQuery("""
                INSERT INTO notificaciones_outbox
                    (solicitud_id, alerta_id, canal, destinatario_tipo, destinatario_id,
                     destino, asunto, cuerpo, estado, intentos)
                VALUES (NULL, :alertaId, :canal, :destinatarioTipo, NULL,
                        :destino, :asunto, :cuerpo, 'PENDIENTE', 0)
                """)
                .setParameter("alertaId", fila.alertaId())
                .setParameter("canal", fila.canal().name())
                .setParameter("destinatarioTipo",
                        fila.canal() == CanalAvisoAlerta.EMAIL ? "USUARIO" : "GRUPO")
                .setParameter("destino", fila.destino())
                .setParameter("asunto", fila.asunto())
                .setParameter("cuerpo", fila.cuerpo())
                .executeUpdate();
    }
}
