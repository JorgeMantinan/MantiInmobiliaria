package es.manti.usuarios.api;

import es.manti.usuarios.application.CuentaGoogleService;
import es.manti.usuarios.domain.CuentaGoogleConflictivaException;
import es.manti.usuarios.domain.CuentaGoogleSinVerificarException;
import es.manti.usuarios.domain.Rol;
import es.manti.usuarios.domain.Usuario;
import es.manti.usuarios.domain.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vínculo de cuenta Google contra PostgreSQL real (no mocks): usuario con
 * password que se vincula al llegar el perfil de Google, conflicto cuando la
 * cuenta ya estaba vinculada a otro google_id y, desde la seguridad
 * pre-release, rechazo del vínculo cuando la cuenta local NO verificó su
 * email (anti pre-hijack / ATO).
 */
@SpringBootTest
@ActiveProfiles("test")
class CuentaGoogleVinculoTest {

    @Autowired
    private CuentaGoogleService cuentaGoogle;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    private String email;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    @Test
    void crearCodigoGoogle_usuarioConPasswordVerificado_vinculaElGoogleId() {
        email = emailNuevo();
        Usuario conPassword = nuevaCuentaPassword(email, true);
        assertThat(conPassword.getGoogleId()).isNull();

        String codigo = cuentaGoogle.crearCodigoGoogle("gid-vinculo-1", email, "Ana Google", true);

        assertThat(codigo).isNotBlank();
        Usuario recargado = usuarios.findByEmail(email).orElseThrow();
        assertThat(recargado.getGoogleId()).isEqualTo("gid-vinculo-1");
        assertThat(recargado.getPasswordHash()).isNotNull(); // conserva el acceso por password
    }

    @Test
    void crearCodigoGoogle_cuentaVinculadaAOtroGoogleId_lanzaConflicto() {
        email = emailNuevo();
        Usuario yaVinculado = nuevaCuentaPassword(email, true);
        yaVinculado.vincularGoogle("gid-original", null);
        usuarios.save(yaVinculado);

        assertThatThrownBy(() -> cuentaGoogle.crearCodigoGoogle("gid-distinto", email, "Otra", true))
                .isInstanceOf(CuentaGoogleConflictivaException.class);

        assertThat(usuarios.findByEmail(email).orElseThrow().getGoogleId())
                .isEqualTo("gid-original");
    }

    @Test
    void crearCodigoGoogle_perfilNuevo_creaLaCuentaVerificada() {
        email = emailNuevo();

        String codigo = cuentaGoogle.crearCodigoGoogle("gid-nuevo-usuario", email, "Nuevo Google", true);

        assertThat(codigo).isNotBlank();
        Usuario creado = usuarios.findByGoogleId("gid-nuevo-usuario").orElseThrow();
        assertThat(creado.getEmail()).isEqualTo(email);
        assertThat(creado.getRol()).isEqualTo(Rol.USER);
        assertThat(creado.getPasswordHash()).isNull();
        // Cuenta creada desde Google con email_verified=true (o ausente)
        assertThat(creado.isEmailVerificado()).isTrue();
    }

    /** Guard anti-ATO: la cuenta local sin verificar NO se vincula con Google. */
    @Test
    void crearCodigoGoogle_cuentaLocalSinVerificar_lanzaCuentaSinVerificar() {
        email = emailNuevo();
        nuevaCuentaPassword(email, false);

        assertThatThrownBy(() -> cuentaGoogle.crearCodigoGoogle("gid-atacante", email, "Atacante", true))
                .isInstanceOf(CuentaGoogleSinVerificarException.class);

        assertThat(usuarios.findByEmail(email).orElseThrow().getGoogleId()).isNull();
    }

    /** El claim explícito false crea la cuenta SIN verificar (no rompe su login por google_id). */
    @Test
    void crearCodigoGoogle_claimEmailVerifiedFalse_creaLaCuentaSinVerificar() {
        email = emailNuevo();

        String codigo = cuentaGoogle.crearCodigoGoogle("gid-sin-verificar", email, "Sin Verificar", false);

        assertThat(codigo).isNotBlank();
        Usuario creado = usuarios.findByGoogleId("gid-sin-verificar").orElseThrow();
        assertThat(creado.isEmailVerificado()).isFalse();

        // Repite el login por google_id: no pasa por el guard de vínculo
        assertThat(cuentaGoogle.crearCodigoGoogle("gid-sin-verificar", email, "Sin Verificar", false))
                .isNotBlank();
    }

    private String emailNuevo() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }

    private Usuario nuevaCuentaPassword(String email, boolean emailVerificado) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setNombre("Ana Test");
        usuario.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        usuario.setRol(Rol.USER);
        usuario.setActivo(true);
        usuario.setEmailVerificado(emailVerificado);
        return usuarios.save(usuario);
    }
}
