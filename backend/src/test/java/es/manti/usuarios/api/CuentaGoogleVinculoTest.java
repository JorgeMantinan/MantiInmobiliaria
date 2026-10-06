package es.manti.usuarios.api;

import es.manti.usuarios.application.CuentaGoogleService;
import es.manti.usuarios.domain.CuentaGoogleConflictivaException;
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
 * password que se vincula al llegar el perfil de Google y conflicto cuando la
 * cuenta ya estaba vinculada a otro google_id.
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
    void crearCodigoGoogle_usuarioConPassword_vinculaElGoogleId() {
        email = emailNuevo();
        Usuario conPassword = nuevaCuentaPassword(email);
        assertThat(conPassword.getGoogleId()).isNull();

        String codigo = cuentaGoogle.crearCodigoGoogle("gid-vinculo-1", email, "Ana Google");

        assertThat(codigo).isNotBlank();
        Usuario recargado = usuarios.findByEmail(email).orElseThrow();
        assertThat(recargado.getGoogleId()).isEqualTo("gid-vinculo-1");
        assertThat(recargado.getPasswordHash()).isNotNull(); // conserva el acceso por password
    }

    @Test
    void crearCodigoGoogle_cuentaVinculadaAOtroGoogleId_lanzaConflicto() {
        email = emailNuevo();
        Usuario yaVinculado = nuevaCuentaPassword(email);
        yaVinculado.vincularGoogle("gid-original", null);
        usuarios.save(yaVinculado);

        assertThatThrownBy(() -> cuentaGoogle.crearCodigoGoogle("gid-distinto", email, "Otra"))
                .isInstanceOf(CuentaGoogleConflictivaException.class);

        assertThat(usuarios.findByEmail(email).orElseThrow().getGoogleId())
                .isEqualTo("gid-original");
    }

    @Test
    void crearCodigoGoogle_perfilNuevo_creaLaCuenta() {
        email = emailNuevo();

        String codigo = cuentaGoogle.crearCodigoGoogle("gid-nuevo-usuario", email, "Nuevo Google");

        assertThat(codigo).isNotBlank();
        Usuario creado = usuarios.findByGoogleId("gid-nuevo-usuario").orElseThrow();
        assertThat(creado.getEmail()).isEqualTo(email);
        assertThat(creado.getRol()).isEqualTo(Rol.USER);
        assertThat(creado.getPasswordHash()).isNull();
    }

    private String emailNuevo() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }

    private Usuario nuevaCuentaPassword(String email) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setNombre("Ana Test");
        usuario.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        usuario.setRol(Rol.USER);
        usuario.setActivo(true);
        return usuarios.save(usuario);
    }
}
