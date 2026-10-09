package es.manti.shared.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escritor de problemas RFC 9457 usado por los entry points y filtros de
 * seguridad (fuera del flujo MVC): status, content-type, campos y escape
 * correcto de comillas, barras, saltos y caracteres de control.
 */
class ProblemResponseWriterTest {

    @Test
    void write_escabezaLosCincoCamposRfc9457() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemResponseWriter.write(response, HttpStatus.UNAUTHORIZED, "no-autenticado",
                "No autenticado", "Debe autenticarse", "/api/v1/inmuebles");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        String json = response.getContentAsString();
        assertThat(json).contains("\"type\":\"https://api.manti.dev/problems/no-autenticado\"");
        assertThat(json).contains("\"title\":\"No autenticado\"");
        assertThat(json).contains("\"status\":401");
        assertThat(json).contains("\"detail\":\"Debe autenticarse\"");
        assertThat(json).contains("\"instance\":\"/api/v1/inmuebles\"");
    }

    @Test
    void write_conCaracteresEspeciales_losEscapaParaJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemResponseWriter.write(response, HttpStatus.BAD_REQUEST, "x",
                "Comilla \" y barra \\ y salto \n y tab \t y control \u0001",
                "detalle", "/i");

        String json = response.getContentAsString();
        assertThat(json).contains("Comilla \\\" y barra \\\\ y salto \\n y tab \\t y control \\u0001");
    }

    @Test
    void write_conValoresNulos_escribeCadenasVacias() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemResponseWriter.write(response, HttpStatus.INTERNAL_SERVER_ERROR,
                "error-interno", null, null, null);

        String json = response.getContentAsString();
        assertThat(json).contains("\"title\":\"\"");
        assertThat(json).contains("\"detail\":\"\"");
        assertThat(json).contains("\"instance\":\"\"");
        assertThat(json).contains("\"status\":500");
    }
}