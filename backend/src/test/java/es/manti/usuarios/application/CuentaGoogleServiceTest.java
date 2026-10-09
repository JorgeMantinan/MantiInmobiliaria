package es.manti.usuarios.application;

import es.manti.usuarios.domain.CodigoInvalidoException;
import es.manti.usuarios.domain.CodigoOAuth2Port;
import es.manti.usuarios.domain.Rol;
import es.manti.usuarios.domain.Usuario;
import es.manti.usuarios.domain.UsuarioInactivoException;
import es.manti.usuarios.domain.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caso de uso de login con Google: normalización del email, vínculo con la
 * cuenta existente, reutilización de la cuenta ya vinculada y canje del
 * código de un solo uso (inexistente, borrada o desactivada).
 */
@ExtendWith(MockitoExtension.class)
class CuentaGoogleServiceTest {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final AuthProperties PROPIEDADES = new AuthProperties(null, null,
            new AuthProperties.Oauth2(new AuthProperties.Google("http://localhost:3000", TTL)),
            null);

    // unidad de aislamiento; el vínculo real está cubierto por CuentaGoogleVinculoTest
    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private CodigoOAuth2Port codigos;

    private CuentaGoogleService service;

    @BeforeEach
    void configurar() {
        service = new CuentaGoogleService(usuarios, codigos, PROPIEDADES);
    }

    @Test
    void crearCodigoGoogle_creaLaCuentaNormalizandoElEmail() {
        when(usuarios.findByGoogleId("g-123")).thenReturn(Optional.empty());
        when(usuarios.findByEmail("ana@example.com")).thenReturn(Optional.empty());
        when(usuarios.save(any(Usuario.class)))
                .thenAnswer(invocation -> {
                    Usuario u = invocation.getArgument(0);
                    u.setId(1L);
                    return u;
                });
        when(codigos.crear(eq(1L), eq(TTL))).thenReturn("cod-opaco");

        String codigo = service.crearCodigoGoogle("g-123", "  Ana@Example.COM ", "Ana", false);

        assertThat(codigo).isEqualTo("cod-opaco");
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(captor.capture());
        Usuario guardado = captor.getValue();
        assertThat(guardado.getEmail()).isEqualTo("ana@example.com");
        assertThat(guardado.getNombre()).isEqualTo("Ana");
        assertThat(guardado.getGoogleId()).isEqualTo("g-123");
        assertThat(guardado.getRol()).isEqualTo(Rol.USER);
        assertThat(guardado.isActivo()).isTrue();
        // fail-closed (PR #59): claim email_verified ausente -> la cuenta NO queda verificada
        assertThat(guardado.isEmailVerificado()).isFalse();
    }

    @Test
    void crearCodigoGoogle_conNombreEnBlanco_usaElEmailComoNombre() {
        when(usuarios.findByGoogleId("g-123")).thenReturn(Optional.empty());
        when(usuarios.findByEmail("ana@example.com")).thenReturn(Optional.empty());
        when(usuarios.save(any(Usuario.class)))
                .thenAnswer(invocation -> {
                    Usuario u = invocation.getArgument(0);
                    u.setId(1L);
                    return u;
                });
        when(codigos.crear(eq(1L), eq(TTL))).thenReturn("cod-opaco");

        service.crearCodigoGoogle("g-123", "ana@example.com", "   ", true);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(captor.capture());
        assertThat(captor.getValue().getNombre()).isEqualTo("ana@example.com");
    }

    @Test
    void crearCodigoGoogle_conCuentaExistentePorEmail_laVinculaSinCambiarElNombre() {
        Usuario existente = usuario(10L, "ana@example.com", "Ana García", true);
        // guard de #59: el vínculo sólo se permite con la cuenta local ya verificada
        existente.setEmailVerificado(true);
        when(usuarios.findByGoogleId("g-123")).thenReturn(Optional.empty());
        when(usuarios.findByEmail("ana@example.com")).thenReturn(Optional.of(existente));
        when(usuarios.save(any(Usuario.class))).thenReturn(existente);
        when(codigos.crear(eq(10L), eq(TTL))).thenReturn("cod-opaco");

        service.crearCodigoGoogle("g-123", "ana@example.com", "Ana de Google", true);

        // nombre propio no vacío → no se pisa con el del perfil de Google
        assertThat(existente.getNombre()).isEqualTo("Ana García");
        assertThat(existente.getGoogleId()).isEqualTo("g-123");
    }

    @Test
    void crearCodigoGoogle_yaVinculada_reutilizaLaCuentaSinBuscarPorEmail() {
        Usuario vinculada = usuario(10L, "ana@example.com", "Ana", true);
        vinculada.setGoogleId("g-123");
        when(usuarios.findByGoogleId("g-123")).thenReturn(Optional.of(vinculada));
        when(codigos.crear(eq(10L), eq(TTL))).thenReturn("cod-opaco");

        String codigo = service.crearCodigoGoogle("g-123", "ana@example.com", "Ana", true);

        assertThat(codigo).isEqualTo("cod-opaco");
        verify(usuarios, never()).save(any(Usuario.class));
    }

    @Test
    void crearCodigoGoogle_conCuentaInactiva_lanzaUsuarioInactivo() {
        Usuario inactiva = usuario(10L, "ana@example.com", "Ana", false);
        inactiva.setGoogleId("g-123");
        when(usuarios.findByGoogleId("g-123")).thenReturn(Optional.of(inactiva));

        assertThatThrownBy(() -> service.crearCodigoGoogle("g-123", "ana@example.com", "Ana", true))
                .isInstanceOf(UsuarioInactivoException.class);
    }

    @Test
    void usuarioPorCodigo_codigoInexistente_lanzaCodigoInvalido() {
        when(codigos.consumir("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.usuarioPorCodigo("nadie"))
                .isInstanceOf(CodigoInvalidoException.class);
    }

    @Test
    void usuarioPorCodigo_cuentaBorrada_lanzaCodigoInvalido() {
        when(codigos.consumir("cod")).thenReturn(Optional.of(99L));
        when(usuarios.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.usuarioPorCodigo("cod"))
                .isInstanceOf(CodigoInvalidoException.class);
    }

    @Test
    void usuarioPorCodigo_cuentaInactiva_lanzaUsuarioInactivo() {
        when(codigos.consumir("cod")).thenReturn(Optional.of(10L));
        when(usuarios.findById(10L))
                .thenReturn(Optional.of(usuario(10L, "a@b.c", "Ana", false)));

        assertThatThrownBy(() -> service.usuarioPorCodigo("cod"))
                .isInstanceOf(UsuarioInactivoException.class);
    }

    private static Usuario usuario(Long id, String email, String nombre, boolean activo) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setEmail(email);
        usuario.setNombre(nombre);
        usuario.setRol(Rol.USER);
        usuario.setActivo(activo);
        return usuario;
    }
}