package es.manti.usuarios.api;

import es.manti.usuarios.application.AuthService;
import es.manti.usuarios.application.RegistroCommand;
import es.manti.usuarios.domain.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registro concurrente: dos peticiones pasan la comprobación
 * {@code existsByEmail} a la vez y la segunda choca con la constraint
 * {@code uq_usuarios_email}. Se simula la carrera de forma determinista con un
 * spy del repositorio que hace pasar {@code existsByEmail} por falso aunque el
 * email ya exista en BD, provocando el {@code DataIntegrityViolationException}
 * real del INSERT (que el handler global mapea a 409).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistroConflictivoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private UsuarioRepository usuarios;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    @Test
    void registroConcurrente_devuelve409ProblemDetail() throws Exception {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        // El "primer" registro gana la carrera
        authService.registrar(new RegistroCommand("Ana Test", email, "secreto123"));

        // El "segundo" ve existsByEmail=false (carrera) pero el INSERT ya no puede:
        // choca con uq_usuarios_email y estalla en DataIntegrityViolationException
        doReturn(false).when(usuarios).existsByEmail(anyString());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Otra Persona","email":"%s","password":"secreto123"}
                                """.formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflicto"));
    }
}
