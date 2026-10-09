package es.manti.usuarios.application;

import es.manti.usuarios.domain.Rol;
import es.manti.usuarios.domain.Usuario;
import es.manti.usuarios.domain.UsuarioInactivoException;
import es.manti.usuarios.domain.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

/**
 * Ventana TOCTOU de la sesión: entre la primera lectura del login (la que usa
 * BCrypt) y la emisión del token puede pasar un tiempo (100 ms de CPU de hash,
 * un logout administrativo en paralelo, un borrado…). La emisión recarga al
 * usuario dentro de su transacción, así que una cuenta desactivada en esa
 * ventana NO llega a tener sesión y el rol del claim es el de la fila fresca.
 *
 * <p>El caso interesante es el segundo test: la primera lectura se hace ver
 * ACTIVA con un spy, mientras que en la BD ya está desactivada. Sin la
 * relectura de la emisión el login terminaría bien y el test fallaría.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthToctouTest {

    private static final String PASSWORD = "secreto123";

    @MockitoSpyBean
    private UsuarioRepository usuarios;

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private String email;

    @BeforeEach
    void crearUsuarioActivo() {
        email = "it-toctou-" + UUID.randomUUID() + "@example.com";
        jdbc.update("INSERT INTO usuarios (email, nombre, password_hash, rol, activo) "
                        + "VALUES (?, ?, ?, 'USER', true)",
                email, "Toctou Test", passwordEncoder.encode(PASSWORD));
    }

    @AfterEach
    void limpiar() {
        // Los stubs del spy no pueden pasar de un test a otro
        reset(usuarios);
        // Los refresh tokens del usuario caen en cascada
        jdbc.update("DELETE FROM usuarios WHERE email = ?", email);
    }

    @Test
    void login_conCuentaDesactivadaAntesDeLlamar_noEmiteSesion() {
        desactivarEnBaseDeDatos();

        assertThatThrownBy(() -> authService.login(email, PASSWORD))
                .isInstanceOf(UsuarioInactivoException.class);

        assertThat(refreshTokensEmitidos()).isZero();
    }

    /**
     * El caso real de TOCTOU: la lectura que usa BCrypt ve la cuenta ACTIVA y
     * la relectura de la emisión, ya dentro de la transacción, la ve
     * DESACTIVADA → 401 y cero refresh tokens.
     */
    @Test
    void login_conCuentaDesactivadaEntreLaLecturaYLaEmision_noEmiteSesion() {
        Long id = jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", Long.class, email);
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM usuarios WHERE email = ?", String.class, email);

        Usuario comoSeLeio = usuarioActivo(id, email, hash);
        doReturn(Optional.of(comoSeLeio)).when(usuarios).findByEmail(email);

        desactivarEnBaseDeDatos();

        assertThatThrownBy(() -> authService.login(email, PASSWORD))
                .as("la emisión debe releer el usuario y rechazar la sesión")
                .isInstanceOf(UsuarioInactivoException.class);

        assertThat(refreshTokensEmitidos()).isZero();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Copia de la fila TAL Y COMO SE LEYÓ antes de que se desactivara. */
    private static Usuario usuarioActivo(Long id, String email, String hash) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setEmail(email);
        usuario.setNombre("Toctou Test");
        usuario.setPasswordHash(hash);
        usuario.setRol(Rol.USER);
        usuario.setActivo(true);
        return usuario;
    }

    private void desactivarEnBaseDeDatos() {
        jdbc.update("UPDATE usuarios SET activo = false WHERE email = ?", email);
    }

    private int refreshTokensEmitidos() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE usuario_id = "
                        + "(SELECT id FROM usuarios WHERE email = ?)",
                Integer.class, email);
    }
}
