package es.manti.profesionales.infra.adapter;

import es.manti.profesionales.domain.EstadoNotificacion;
import es.manti.profesionales.domain.NotificacionOutbox;
import es.manti.profesionales.domain.NotificacionOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador JPA del puerto {@link NotificacionOutboxRepository}.
 *
 * <p>Las dos escrituras de estado son {@code UPDATE} masivos: no hace falta
 * re-cargar la fila ni hay riesgo de pisar campos con una entidad obsoleta en
 * la sesión. Exigen transacción activa, que abre siempre el servicio (nunca el
 * despachador directamente).</p>
 */
@Repository
@RequiredArgsConstructor
public class NotificacionOutboxRepositoryAdapter implements NotificacionOutboxRepository {

    private final NotificacionOutboxJpaRepository jpaRepository;

    @Override
    public NotificacionOutbox guardar(NotificacionOutbox fila) {
        return jpaRepository.save(fila);
    }

    @Override
    public List<NotificacionOutbox> pendientes(int lote) {
        return jpaRepository.pendientes(EstadoNotificacion.PENDIENTE, PageRequest.of(0, lote));
    }

    @Override
    public Optional<NotificacionOutbox> porId(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void marcarEnviada(Long id, int intentos) {
        jpaRepository.marcarEnviada(id, EstadoNotificacion.ENVIADA, (short) intentos,
                Instant.now());
    }

    @Override
    public void marcarFalla(Long id, int intentos, String ultimoError, boolean fallida) {
        EstadoNotificacion estado = fallida
                ? EstadoNotificacion.FALLIDA
                : EstadoNotificacion.PENDIENTE;
        jpaRepository.marcarFalla(id, estado, (short) intentos, ultimoError);
    }
}
