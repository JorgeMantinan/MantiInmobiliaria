package es.manti.inmuebles.infra.adapter;

import es.manti.inmuebles.domain.AlertaNotificacionRepository;
import es.manti.inmuebles.domain.CanalAvisoAlerta;
import es.manti.inmuebles.domain.FilaAvisoAlerta;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
 * <p>La lectura del email del usuario es también nativa sobre
 * {@code usuarios}: la entidad ajena jamás se importa.</p>
 */
@Repository
@RequiredArgsConstructor
public class AlertaNotificacionAdapter implements AlertaNotificacionRepository {

    private final EntityManager entityManager;

    @Override
    public Optional<String> emailDeUsuario(Long usuarioId) {
        List<String> emails = entityManager
                .createNativeQuery(
                        "SELECT email FROM usuarios WHERE id = :id AND activo = true", String.class)
                .setParameter("id", usuarioId)
                .getResultList();
        return emails.stream().findFirst();
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
