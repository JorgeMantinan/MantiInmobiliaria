package es.manti.shared.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Todos los handlers del advice GENÉRICO: validación de body y de parámetros,
 * parámetro ausente/incorrecto, cuerpo ilegible, recursos/métodos/tipos no
 * soportados, integridad, seguridad y catch-all de 500 — servidos por un
 * controlador dummy que dispara cada excepción. Cada problema sale como
 * {@code application/problem+json} RFC 9457 sin filtrar detalles internos.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void configurar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void parametroFaltante_devuelve400ConElNombreDelParametro() throws Exception {
        mockMvc.perform(get("/dummy/falta-param"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/parametro-faltante"))
                .andExpect(jsonPath("$.title").value("Parámetro obligatorio ausente"))
                .andExpect(jsonPath("$.errors[0].field").value("obligatorio"))
                .andExpect(jsonPath("$.errors[0].message").value("parámetro obligatorio ausente"));
    }

    @Test
    void parametroIncorrecto_devuelve400ConElCampoInvalido() throws Exception {
        mockMvc.perform(get("/dummy/falta-param").param("obligatorio", "x")
                        .param("largo", "no-numero"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/parametro-invalido"))
                .andExpect(jsonPath("$.errors[0].field").value("largo"));
    }

    @Test
    void validacionDeBody_devuelve400ConLosErroresDeCampo() throws Exception {
        mockMvc.perform(post("/dummy/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/validacion"))
                .andExpect(jsonPath("$.errors[0].field").value("nombre"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
    }

    @Test
    void validacionDeParametros_devuelve400DeValidacion() throws Exception {
        mockMvc.perform(get("/dummy/constraint").param("largo", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/validacion"))
                .andExpect(jsonPath("$.errors[0].message").value("valor inválido"));
    }

    @Test
    void cuerpoIlegible_devuelve400DeCuerpoInvalido() throws Exception {
        mockMvc.perform(post("/dummy/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{esto-no-es-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/cuerpo-invalido"))
                .andExpect(jsonPath("$.detail").value("JSON malformado o tipo de campo incorrecto"));
    }

    @Test
    void metodoNoSoportado_devuelve405() throws Exception {
        mockMvc.perform(post("/dummy/falta-param"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/metodo-no-soportado"));
    }

    @Test
    void tipoNoSoportado_devuelve415() throws Exception {
        mockMvc.perform(post("/dummy/tipo-no-soportado"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/tipo-no-soportado"));
    }

    @Test
    void recursoNoEncontrado_devuelve404() throws Exception {
        mockMvc.perform(get("/dummy/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/no-encontrado"))
                .andExpect(jsonPath("$.detail").value("La ruta solicitada no existe"));
    }

    @Test
    void violacionDeIntegridad_devuelve409SinDetallesInternos() throws Exception {
        mockMvc.perform(post("/dummy/integridad"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/conflicto-integridad"))
                .andExpect(jsonPath("$.detail")
                        .value("Los datos entran en conflicto con el estado actual"));
    }

    @Test
    void accesoDenegado_devuelve403() throws Exception {
        mockMvc.perform(post("/dummy/denegado"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/acceso-denegado"));
    }

    @Test
    void sinAutenticar_devuelve401() throws Exception {
        mockMvc.perform(post("/dummy/no-autenticado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/no-autenticado"));
    }

    @Test
    void errorInesperado_devuelve500SinFiltrarElMensajeInterno() throws Exception {
        mockMvc.perform(post("/dummy/inesperado"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/error-interno"))
                .andExpect(jsonPath("$.detail")
                        .value("Se ha producido un error inesperado"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("detalle secreto"))));
    }

    // ------------------------------------------------------------------
    // Controlador dummy: un endpoint por excepción que debe casar el advice
    // ------------------------------------------------------------------

    @RestController
    static class DummyController {

        record DtoValidar(@NotBlank String nombre) {
        }

        @GetMapping("/dummy/falta-param")
        String faltaParam(@RequestParam String obligatorio,
                          @RequestParam(required = false) Integer largo) {
            return "ok";
        }

        @GetMapping("/dummy/constraint")
        String constraint(@Positive Integer largo) {
            return "ok";
        }

        @PostMapping("/dummy/validar")
        String validar(@Valid @RequestBody DtoValidar dto) {
            return "ok";
        }

        @PostMapping("/dummy/tipo-no-soportado")
        String tipoNoSoportado() throws Exception {
            throw new org.springframework.web.HttpMediaTypeNotSupportedException(
                    "application/xml no soportado");
        }

        @GetMapping("/dummy/no-existe")
        String noExiste() throws Exception {
            throw new NoResourceFoundException(HttpMethod.GET, "/dummy/no-existe",
                    "No se encontró el recurso");
        }

        @PostMapping("/dummy/integridad")
        String integridad() {
            throw new DataIntegrityViolationException("uq_usuarios_email");
        }

        @PostMapping("/dummy/denegado")
        String denegado() {
            throw new AccessDeniedException("sin permisos");
        }

        @PostMapping("/dummy/no-autenticado")
        String noAutenticado() {
            throw new BadCredentialsException("falta token");
        }

        @PostMapping("/dummy/inesperado")
        String inesperado() {
            throw new IllegalStateException("detalle secreto del negocio");
        }
    }
}