package es.manti.swipes.application;

import es.manti.swipes.domain.DecisionSwipe;
import es.manti.swipes.domain.Swipe;
import es.manti.swipes.domain.SwipeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unitario de la <b>carrera de inserción</b> del POST (select-then-insert
 * contra {@code uq_swipes_usuario_inmueble}): el doble tap optimista del
 * frontend puede hacer que dos peticiones vean la pareja vacía e intenten las
 * dos insertar. La segunda recibe {@link DataIntegrityViolationException} y el
 * servicio debe releer en una transacción NUEVA y responder con semántica de
 * 200, no dejar que el choque llegue como 409.
 *
 * <p>Sin contexto de Spring: repositorio y gestor de transacciones mockeados,
 * el {@link TransactionTemplate} es real (el gestor devuelto sólo absorbe las
 * llamadas de ciclo de vida).</p>
 */
class SwipeServiceIdempotenciaTest {

    private static final long USUARIO = 7L;
    private static final long INMUEBLE = 42L;

    private final SwipeRepository repository = mock(SwipeRepository.class);
    private final SwipeService service = new SwipeService(repository, plantilla());

    @Test
    void registrar_carreraDeInsercion_releeYDevuelveSemanticaDe200() {
        DataIntegrityViolationException choque =
                new DataIntegrityViolationException("uq_swipes_usuario_inmueble");
        when(repository.existeInmuebleVigente(INMUEBLE)).thenReturn(true);
        when(repository.porUsuarioEInmueble(USUARIO, INMUEBLE))
                // 1ª lectura: los dos concurrentes ven la pareja vacía
                .thenReturn(Optional.empty())
                // relectura tras el choque: ya está la fila del ganador
                .thenReturn(Optional.of(Swipe.nuevo(USUARIO, INMUEBLE, DecisionSwipe.LIKE)));
        when(repository.guardar(any(Swipe.class))).thenThrow(choque);

        SwipeResultado resultado = service.registrar(USUARIO, INMUEBLE, DecisionSwipe.LIKE);

        // 200, no 201: la fila ya existía cuando se releyó
        assertThat(resultado.creada()).isFalse();
        assertThat(resultado.inmuebleId()).isEqualTo(INMUEBLE);
        assertThat(resultado.decision()).isEqualTo(DecisionSwipe.LIKE);
        verify(repository, times(2)).porUsuarioEInmueble(USUARIO, INMUEBLE);
        verify(repository, times(1)).guardar(any(Swipe.class));
    }

    @Test
    void registrar_carreraConDecisionDistinta_actualizaLaFilaGanadora() {
        Swipe ganadora = Swipe.nuevo(USUARIO, INMUEBLE, DecisionSwipe.LIKE);
        when(repository.existeInmuebleVigente(INMUEBLE)).thenReturn(true);
        when(repository.porUsuarioEInmueble(USUARIO, INMUEBLE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ganadora));
        doThrow(new DataIntegrityViolationException("uq_swipes_usuario_inmueble"))
                .doReturn(ganadora)
                .when(repository).guardar(any(Swipe.class));

        SwipeResultado resultado = service.registrar(USUARIO, INMUEBLE, DecisionSwipe.NOPE);

        assertThat(resultado.creada()).isFalse();
        assertThat(resultado.decision()).isEqualTo(DecisionSwipe.NOPE);
        assertThat(ganadora.getDecision()).isEqualTo(DecisionSwipe.NOPE);
        verify(repository, times(2)).guardar(any(Swipe.class));
    }

    @Test
    void registrar_carreraSinFilaEnLaRelectura_reLanzaElChoquePara409() {
        DataIntegrityViolationException choque =
                new DataIntegrityViolationException("uq_swipes_usuario_inmueble");
        when(repository.existeInmuebleVigente(INMUEBLE)).thenReturn(true);
        when(repository.porUsuarioEInmueble(USUARIO, INMUEBLE)).thenReturn(Optional.empty());
        when(repository.guardar(any(Swipe.class))).thenThrow(choque);

        // Retry ÚNICO: si la relectura sigue vacía, el choque se propaga (409)
        assertThatThrownBy(() -> service.registrar(USUARIO, INMUEBLE, DecisionSwipe.LIKE))
                .isSameAs(choque);
        verify(repository, times(2)).porUsuarioEInmueble(USUARIO, INMUEBLE);
    }

    @Test
    void registrar_sinCarrera_noTocaElSegundoCarrilDeReintento() {
        when(repository.existeInmuebleVigente(INMUEBLE)).thenReturn(true);
        when(repository.porUsuarioEInmueble(USUARIO, INMUEBLE)).thenReturn(Optional.empty());
        when(repository.guardar(any(Swipe.class)))
                .thenReturn(Swipe.nuevo(USUARIO, INMUEBLE, DecisionSwipe.LIKE));

        SwipeResultado primera = service.registrar(USUARIO, INMUEBLE, DecisionSwipe.LIKE);

        assertThat(primera.creada()).isTrue();
        verify(repository, times(1)).porUsuarioEInmueble(USUARIO, INMUEBLE);
        verify(repository, times(1)).guardar(any(Swipe.class));
    }

    /** Gestor real de transacciones sólo para que el {@link TransactionTemplate} arranque. */
    private static TransactionTemplate plantilla() {
        PlatformTransactionManager gestor = mock(PlatformTransactionManager.class);
        when(gestor.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return new TransactionTemplate(gestor);
    }
}
