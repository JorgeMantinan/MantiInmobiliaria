package es.manti.profesionales.application;

import es.manti.profesionales.domain.EmailProfesionalYaRegistradoException;
import es.manti.profesionales.domain.Gremio;
import es.manti.profesionales.domain.Profesional;
import es.manti.profesionales.domain.ProfesionalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Registro PÚBLICO de profesionales: crea la fila en estado
 * {@code PENDIENTE} y la notificación llega cuando un ADMIN la active.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistroProfesionalService {

    private final ProfesionalRepository profesionales;
    private final GremioService gremios;

    /**
     * Alta de un profesional (anónimo, con rate limit por IP).
     *
     * @return el profesional creado, siempre {@code PENDIENTE}
     * @throws EmailProfesionalYaRegistradoException 409 si el email ya existe
     * @throws es.manti.profesionales.domain.GremioInexistenteException 400 si
     *         algún gremio ofertado no existe
     */
    @Transactional
    public Profesional registrar(RegistroProfesionalCommand comando) {
        if (profesionales.existeEmail(comando.email())) {
            throw new EmailProfesionalYaRegistradoException();
        }
        List<Gremio> pedidos = gremios.resolver(comando.gremios());
        // Carrera entre el existsByEmail y el INSERT: la UNIQUE
        // (uq_profesionales_email) devuelve 409 igualmente vía
        // GlobalExceptionHandler#violacionDeIntegridad.
        Profesional creado = profesionales.guardar(Profesional.nuevo(
                comando.nombre(), comando.email(), comando.telefono(),
                comando.municipio(), pedidos));
        log.info("Profesional {} registrado como PENDIENTE", creado.getId());
        return creado;
    }
}
