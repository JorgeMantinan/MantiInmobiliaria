package es.manti.inmuebles;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API de alertas de búsqueda con MockMvc contra PostgreSQL real (issue #96):
 * 401 anónimo en los CUATRO endpoints, alta con la validación REUTILIZADA del
 * listado, owner-scoped (404 en alertas ajenas), edición parcial, borrado y
 * el techo de alertas por usuario.
 *
 * <p>Los usuarios se crean con el registro real (token de verdad, nada de
 * {@code @WithMockUser}) y las fixtures usan prefijos {@code it-alerta-}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertaApiTest {

    private static final String PASSWORD = "secreto123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiarFixtures() {
        FixturesAlertas.limpiar(jdbc);
    }

    // ------------------------------------------------------------------
    // Seguridad: sin token -> 401 en los cuatro endpoints
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0} {1} sin token -> 401")
    @CsvSource({
            "GET,    /api/v1/alertas",
            "POST,   /api/v1/alertas",
            "PATCH,  /api/v1/alertas/1",
            "DELETE, /api/v1/alertas/1"
    })
    void endpoints_sinToken_devuelven401(String metodo, String ruta) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .request(HttpMethod.valueOf(metodo), URI.create(ruta))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"x\",\"filtros\":{}}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ------------------------------------------------------------------
    // POST /api/v1/alertas
    // ------------------------------------------------------------------

    @Test
    void crear_devuelve201LocationYLaAlertaPersistidaConSuMarcaDeAgua() throws Exception {
        String token = registrarUsuario();
        long usuario = idUsuario(token);
        long maximoAntes = FixturesAlertas.maximoIdInmuebles(jdbc);

        String cuerpo = mockMvc.perform(post("/api/v1/alertas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Pisos Madrid",
                                  "filtros": {
                                    "estado": "VENTA", "tipo": "PISO", "municipio": "Madrid",
                                    "precioMin": 100000, "precioMax": 250000,
                                    "habitacionesMin": 3, "destacado": true
                                  }
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/alertas/")))
                .andExpect(jsonPath("$.nombre").value("Pisos Madrid"))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.ultimoAvisoEn").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.filtros.estado").value("VENTA"))
                .andExpect(jsonPath("$.filtros.tipo").value("PISO"))
                .andExpect(jsonPath("$.filtros.municipio").value("Madrid"))
                .andExpect(jsonPath("$.filtros.destacado").value(true))
                .andReturn().getResponse().getContentAsString();

        long id = idDe(cuerpo, "$.id");
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT * FROM alertas_busqueda WHERE id = ?", id);
        assertThat(((Number) fila.get("usuario_id")).longValue()).isEqualTo(usuario);
        assertThat(fila.get("nombre")).isEqualTo("Pisos Madrid");
        assertThat(fila.get("municipio")).isEqualTo("Madrid");
        assertThat(fila.get("activa")).isEqualTo(Boolean.TRUE);
        // Marca de agua = mayor id existente AL GUARDAR: sólo se avisará de lo
        // que entre después (issue #96).
        assertThat(((Number) fila.get("ultimo_inmueble_avisado_id")).longValue())
                .isEqualTo(maximoAntes);
        assertThat(fila.get("ultimo_aviso_en")).isNull();
    }

    @Test
    void crear_rangoDePreciosInvalido_responde400ComoElListado() throws Exception {
        String token = registrarUsuario();

        // La MISMA rutina de validación que GET /api/v1/inmuebles.
        mockMvc.perform(post("/api/v1/alertas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Rango raro",
                                 "filtros":{"precioMin":500000,"precioMax":100000}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"))
                .andExpect(jsonPath("$.errors[0].field").value("precioMin"));

        assertThat(contarAlertas(token)).isZero();
    }

    @Test
    void crear_precioNegativo_responde400ConElCampo() throws Exception {
        String token = registrarUsuario();

        mockMvc.perform(post("/api/v1/alertas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Negativo","filtros":{"precioMax":-1}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"))
                .andExpect(jsonPath("$.errors[0].field").value("precioMax"));
    }

    @Test
    void crear_nombreEnBlanco_responde400DeValidacion() throws Exception {
        String token = registrarUsuario();

        mockMvc.perform(post("/api/v1/alertas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  \",\"filtros\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.errors[0].field").value("nombre"));
    }

    @Test
    void crear_municipioDemasiadoLargo_responde400DeValidacion() throws Exception {
        String token = registrarUsuario();
        String largo = "m".repeat(121);

        mockMvc.perform(post("/api/v1/alertas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Largo","filtros":{"municipio":"%s"}}""".formatted(largo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.errors[0].field").value("filtros.municipio"));
    }

    @Test
    void crear_masDeCincuentaAlertas_responde409() throws Exception {
        String token = registrarUsuario();
        long usuario = idUsuario(token);
        jdbc.update("""
                INSERT INTO alertas_busqueda (usuario_id, nombre, ultimo_inmueble_avisado_id)
                SELECT ?, 'Alerta ' || s, 0 FROM generate_series(1, 50) s
                """, usuario);

        mockMvc.perform(post("/api/v1/alertas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"La 51\",\"filtros\":{}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Límite de alertas alcanzado"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("50")));
    }

    // ------------------------------------------------------------------
    // GET /api/v1/alertas
    // ------------------------------------------------------------------

    @Test
    void listar_devuelveSoloLasAlertasDelUsuarioActual() throws Exception {
        String tokenA = registrarUsuario();
        String tokenB = registrarUsuario();
        crearAlerta(tokenA, "Alerta de A");
        crearAlerta(tokenB, "Alerta de B");

        mockMvc.perform(get("/api/v1/alertas")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Alerta de A"));
    }

    @Test
    void listar_sinAlertas_devuelveArrayVacio() throws Exception {
        String token = registrarUsuario();

        mockMvc.perform(get("/api/v1/alertas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------------
    // PATCH y DELETE (owner-scoped: 404 en ajeno, nunca 403)
    // ------------------------------------------------------------------

    @Test
    void actualizar_cambiaNombreYActividad_sinTocarLosFiltros() throws Exception {
        String token = registrarUsuario();
        long id = crearAlerta(token, "Nombre original");

        mockMvc.perform(patch("/api/v1/alertas/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Renombrada\",\"activa\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Renombrada"))
                .andExpect(jsonPath("$.activa").value(false))
                .andExpect(jsonPath("$.filtros.municipio").value("Madrid"));

        // Edición parcial: sólo activa, el nombre NO cambia.
        mockMvc.perform(patch("/api/v1/alertas/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Renombrada"))
                .andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    void actualizar_alertaDeOtroUsuario_devuelve404() throws Exception {
        String tokenA = registrarUsuario();
        String tokenB = registrarUsuario();
        long id = crearAlerta(tokenA, "Privada");

        mockMvc.perform(patch("/api/v1/alertas/{id}", id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Hackeada\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("No encontrado"));

        assertThat(nombreDe(id)).isEqualTo("Privada");
    }

    @Test
    void actualizar_inexistente_devuelve404() throws Exception {
        String token = registrarUsuario();

        mockMvc.perform(patch("/api/v1/alertas/{id}", 987_654_321L)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminar_borraLaAlertaYLaDejaInaccesible() throws Exception {
        String token = registrarUsuario();
        long id = crearAlerta(token, "Para borrar");

        mockMvc.perform(delete("/api/v1/alertas/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(contarAlertas(token)).isZero();
        mockMvc.perform(patch("/api/v1/alertas/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminar_alertaDeOtroUsuario_devuelve404YNLaConserva() throws Exception {
        String tokenA = registrarUsuario();
        String tokenB = registrarUsuario();
        long id = crearAlerta(tokenA, "Sigue aquí");

        mockMvc.perform(delete("/api/v1/alertas/{id}", id)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        assertThat(nombreDe(id)).isEqualTo("Sigue aquí");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private long crearAlerta(String token, String nombre) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/alertas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"%s","filtros":{"municipio":"Madrid"}}
                                """.formatted(nombre)))
                .andExpect(status().isCreated())
                .andReturn();
        return idDe(resultado.getResponse().getContentAsString(), "$.id");
    }

    private int contarAlertas(String token) throws Exception {
        String cuerpo = mockMvc.perform(get("/api/v1/alertas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((java.util.List<?>) JsonPath.read(cuerpo, "$")).size();
    }

    private String nombreDe(long id) {
        return jdbc.queryForObject("SELECT nombre FROM alertas_busqueda WHERE id = ?",
                String.class, id);
    }

    private String registrarUsuario() throws Exception {
        String email = "it-alerta-api-" + UUID.randomUUID() + "@example.com";
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Usuario Alerta","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.accessToken");
    }

    /** Id del usuario dueño del token, leído del endpoint /me (claims del JWT). */
    private long idUsuario(String token) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return idDe(resultado.getResponse().getContentAsString(), "$.id");
    }

    /** JsonPath devuelve Integer para enteros pequeños: se normaliza a long. */
    private static long idDe(String json, String expresion) {
        return ((Number) JsonPath.read(json, expresion)).longValue();
    }
}
