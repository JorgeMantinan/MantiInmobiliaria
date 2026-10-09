package es.manti.usuarios.api;

import es.manti.usuarios.application.AuthService;
import es.manti.usuarios.application.CuentaGoogleService;
import es.manti.usuarios.application.RegistroCommand;
import es.manti.usuarios.domain.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo del guard anti-ATO pre-release: se registra una cuenta local
 * con un email y, acto seguido, se simula un login con Google con el MISMO
 * email. El servicio real lanza {@code CuentaGoogleSinVerificarException} y el
 * advice real la devuelve como <b>409 {@code account_conflict}</b> — el mismo
 * código que el redirect OAuth2 y que el frontend ya interpreta, sin cambios de UI.
 *
 * <p>El andamio ({@link LoginGoogleDePrueba}) sólo expone por HTTP la MISMA
 * llamada que hace {@link GoogleOAuth2SuccessHandler} al volver de Google, para
 * poder ejercer el advice con MockMvc sin montar el baile OIDC completo.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class CuentaGoogleSinVerificarTest {

    private static final String PASSWORD = "secreto123";

    @Autowired
    private AuthService auth;

    @Autowired
    private CuentaGoogleService cuentaGoogle;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;
    private String email;

    @BeforeEach
    void preparar() {
        email = "it-" + UUID.randomUUID() + "@example.com";
        mvc = MockMvcBuilders.standaloneSetup(new LoginGoogleDePrueba(cuentaGoogle))
                .setControllerAdvice(new UsuarioErrorAdvice())
                .build();
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    /** (a) Registro local sin verificar → login Google con el mismo email → 409 account_conflict. */
    @Test
    void registrarLocal_luegoLoginGoogle_devuelve409AccountConflict() throws Exception {
        auth.registrar(new RegistroCommand("Ana Test", email, PASSWORD));
        assertThat(usuarios.findByEmail(email).orElseThrow().isEmailVerificado()).isFalse();

        mvc.perform(post("/prueba/login-google")
                        .param("googleId", "gid-pre-hijack")
                        .param("email", email))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://api.manti.dev/problems/account_conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflicto"));

        // La cuenta no queda vinculada: el ataque no progresa
        assertThat(usuarios.findByEmail(email).orElseThrow().getGoogleId()).isNull();
    }

    /** (b) Cuenta VERIFICADA (flag en la fila) → el vínculo con Google se acepta. */
    @Test
    void cuentaLocalVerificada_luegoLoginGoogle_vinculaLaCuenta() throws Exception {
        auth.registrar(new RegistroCommand("Ana Test", email, PASSWORD));
        jdbc.update("UPDATE usuarios SET email_verificado = true WHERE email = ?", email);

        mvc.perform(post("/prueba/login-google")
                        .param("googleId", "gid-verificado")
                        .param("email", email))
                .andExpect(status().isOk());

        assertThat(usuarios.findByEmail(email).orElseThrow().getGoogleId())
                .isEqualTo("gid-verificado");
    }

    /**
     * Andamio de test: replica la llamada que hace el success handler del
     * flujo OAuth2 ({@code crearCodigoGoogle}) para ejercer el advice real.
     */
    @RestController
    static class LoginGoogleDePrueba {

        private final CuentaGoogleService servicio;

        LoginGoogleDePrueba(CuentaGoogleService servicio) {
            this.servicio = servicio;
        }

        @PostMapping("/prueba/login-google")
        void loginGoogle(@RequestParam String googleId, @RequestParam String email) {
            servicio.crearCodigoGoogle(googleId, email, "Ana Google", true);
        }
    }
}
