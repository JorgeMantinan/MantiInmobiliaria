package es.manti.profesionales.application;

import es.manti.profesionales.domain.EstadoProfesional;
import es.manti.profesionales.domain.Profesional;
import es.manti.profesionales.domain.ProfesionalNoEncontradoException;
import es.manti.profesionales.domain.ProfesionalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Backoffice de profesionales: listado por estado y moderación del alta
 * (ACTIVO / RECHAZADO). Sólo lo usan los endpoints con rol ADMIN.
 *
 * <p>Los listados se resuelven en dos queries (página + gremios en bloque) y
 * se devuelven como {@link ProfesionalResumen}: sin N+1 y sin entidades
 * perezosas fuera de la transacción ({@code open-in-view: false}).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GestionProfesionalService {

    private final ProfesionalRepository profesionales;

    /**
     * @param estado {@code PENDIENTE}, {@code ACTIVO} o {@code RECHAZADO};
     *                {@code null} devuelve todos
     */
    @Transactional(readOnly = true)
    public List<ProfesionalResumen> listar(EstadoProfesional estado) {
        List<Profesional> filas = profesionales.porEstado(estado);
        if (filas.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> gremios = profesionales.slugsDeGremios(
                filas.stream().map(Profesional::getId).toList());
        return filas.stream()
                .map(fila -> new ProfesionalResumen(
                        fila.getId(), fila.getNombre(), fila.getEmail(), fila.getTelefono(),
                        fila.getMunicipio(), fila.getEstado(),
                        gremios.getOrDefault(fila.getId(), List.of()), fila.getCreadoEn()))
                .toList();
    }

    /**
     * Moderación de una alta: sólo acepta ACTIVO o RECHAZADO (el DTO ya lo
     * valida con {@code @Pattern}).
     *
     * <p>Control optimista (issue #51): si entre la lectura y la escritura OTRO
     * administrador ha moderado al mismo profesional, el flush choca con la
     * versión leída y la tx aborta con {@code OptimisticLockingFailureException}
     * (409 en el advice del módulo) — nunca se revierte en silencio al
     * primero. El log de éxito sólo se emite si la escritura llegó a committed.</p>
     *
     * @param usuarioActual actor de la moderación, leído del {@code sub} del
     *                       JWT en el controller ({@code Authentication.getName()})
     * @throws ProfesionalNoEncontradoException 404 si el id no existe
     */
    @Transactional
    public Profesional cambiarEstado(Long id, EstadoProfesional estado, String usuarioActual) {
        Profesional profesional = profesionales.porId(id)
                .orElseThrow(() -> new ProfesionalNoEncontradoException(id));
        profesional.cambiarEstado(estado);
        Profesional guardado = profesionales.guardar(profesional);
        log.info("Profesional {} → {} por {}", id, estado, usuarioActual);
        return guardado;
    }
}
