package es.manti.swipes;

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API de swipes con MockMvc contra PostgreSQL real: 401 anónimo en los CUATRO
 * endpoints, validación (400), el ciclo 201/200/204 del contrato, el cursor
 * opaco y el IDOR sobre favoritos ajenos.
 *
 * <p>Los usuarios se crean con fixtures propios (prefijo {@code it-}) y el
 * token sale del registro real: nada de {@code @WithMockUser} — se comprueba la
 * cadena de seguridad de verdad.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SwipeApiTest {

    private static final long ID_INEXISTENTE = 987_654_321L;
    private static final String PASSWORD = "secreto123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiarFixtures() {
        jdbc.update("DELETE FROM inmuebles WHERE municipio LIKE 'It-%'");
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    // ------------------------------------------------------------------
    // Seguridad: sin token -> 401 en los cuatro endpoints
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0} {1} sin token -> 401")
    @CsvSource({
            "POST,   /api/v1/swipes",
            "GET,    /api/v1/swipes/deck",
            "GET,    /api/v1/swipes/favoritos",
            "DELETE, /api/v1/swipes/favoritos/1"
    })
    void endpoints_sinToken_devuelven401(String metodo, String ruta) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(metodo), URI.create(ruta))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inmuebleId\": 1, \"decision\": \"LIKE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ------------------------------------------------------------------
    // POST /api/v1/swipes
    // ------------------------------------------------------------------

    @Test
    void registrar_primeraDecision_devuelve201YNoApareceEnElDeck() throws Exception {
        String token = registrarUsuario();
        long inmueble = inmueble(municipioPrueba());

        String cuerpo = mockMvc.perform(post("/api/v1/swipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(inmueble, "LIKE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("LIKE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(idDe(cuerpo, "$.inmuebleId")).isEqualTo(inmueble);
        assertThat(idsDeck(token)).doesNotContain(inmueble);
    }

    @Test
    void registrar_repeticionMismaDecision_devuelve200SinFilaNueva() throws Exception {
        String token = registrarUsuario();
        long usuario = idUsuario(token);
        long inmueble = inmueble(municipioPrueba());

        mockMvc.perform(post("/api/v1/swipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(inmueble, "LIKE")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/swipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(inmueble, "LIKE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("LIKE"));

        assertThat(contarSwipes(usuario, inmueble)).isEqualTo(1);
    }

    @Test
    void registrar_cambioDeDecision_devuelve200ActualizandoLaFila() throws Exception {
        String token = registrarUsuario();
        long usuario = idUsuario(token);
        long inmueble = inmueble(municipioPrueba());

        mockMvc.perform(post("/api/v1/swipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(inmueble, "LIKE")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/swipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(inmueble, "NOPE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("NOPE"));

        assertThat(contarSwipes(usuario, inmueble)).isEqualTo(1);
        assertThat(decisionDe(usuario, inmueble)).isEqualTo("NOPE");

        // El like ya no está en favoritos
        mockMvc.perform(get("/api/v1/swipes/favoritos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void registrar_decisionNoAdmitida_devuelve400ConErrors() throws Exception {
        String token = registrarUsuario();
        long inmueble = inmueble(municipioPrueba());

        mockMvc.perform(post("/api/v1/swipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(inmueble, "MAYBE")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.errors[0].field").value("decision"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void registrar_sinInmuebleId_devuelve400ConErrors() throws Exception {
        String token = registrarUsuario();

        mockMvc.perform(post("/api/v1/swipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\": \"LIKE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("inmuebleId"));
    }

    @Test
    void registrar_inmuebleInexistente_devuelve404ProblemDetail() throws Exception {
        String token = registrarUsuario();

        mockMvc.perform(post("/api/v1/swipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(ID_INEXISTENTE, "LIKE")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No encontrado"));
    }

    @Test
    void registrar_inmuebleEliminado_devuelve404() throws Exception {
        String token = registrarUsuario();
        long inmueble = inmueble(municipioPrueba());
        jdbc.update("UPDATE inmuebles SET eliminado_en = now() WHERE id = ?", inmueble);

        mockMvc.perform(post("/api/v1/swipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(inmueble, "LIKE")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // GET /api/v1/swipes/deck
    // ------------------------------------------------------------------

    @Test
    void deck_devuelveEnvelopeConInmuebleSummaryYCursor() throws Exception {
        String token = registrarUsuario();
        long usuario = idUsuario(token);
        String municipio = municipioPrueba();
        // El deck de un usuario recién creado es TODO el catálogo activo (los
        // seeds V900 en dev, una BD casi vacía en CI). Se le swipea lo que ya
        // existía antes de crear las fixtures para que el tamaño de página, el
        // cursor y totalElements dependan sólo de ESTE test en cualquier entorno.
        descartarCatalogoPreexistente(usuario);
        long masViejo = inmueble(municipio);
        long uno = inmueble(municipio);
        long dos = inmueble(municipio);

        String cuerpo = mockMvc.perform(get("/api/v1/swipes/deck")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                // Mismo resumen que el listado público: sin descripción ni dirección
                .andExpect(jsonPath("$.content[0].titulo").value("it-piso de prueba"))
                .andExpect(jsonPath("$.content[0].tipo").value("PISO"))
                .andExpect(jsonPath("$.content[0].estado").value("VENTA"))
                .andExpect(jsonPath("$.content[0].descripcion").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").isString())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andReturn().getResponse().getContentAsString();

        assertThat(idsDe(cuerpo)).containsExactly(dos, uno);
        assertThat((String) JsonPath.read(cuerpo, "$.nextCursor")).isNotBlank();
        // Tercera fila más antigua: se queda tras el cursor y por eso éste existe
        assertThat(idsDeck(token)).containsExactly(dos, uno, masViejo);
    }

    @Test
    void deck_limitFueraDeRango_devuelve400ConErrors() throws Exception {
        String token = registrarUsuario();

        mockMvc.perform(get("/api/v1/swipes/deck")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Parámetro inválido"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"));

        mockMvc.perform(get("/api/v1/swipes/deck")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));

        mockMvc.perform(get("/api/v1/swipes/favoritos")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
    }

    @Test
    void deck_cursorCorrupto_devuelve400ConProblemDetail() throws Exception {
        String token = registrarUsuario();

        mockMvc.perform(get("/api/v1/swipes/deck")
                        .header("Authorization", "Bearer " + token)
                        .param("cursor", "dGV4dG9xdWUtY29ycnVwdG8"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Parámetro inválido"))
                .andExpect(jsonPath("$.errors[0].field").value("cursor"));
    }

    // ------------------------------------------------------------------
    // GET /api/v1/swipes/favoritos + DELETE
    // ------------------------------------------------------------------

    @Test
    void favoritos_devuelveSoloLikesDelUsuarioConElEnvelope() throws Exception {
        String token = registrarUsuario();
        String municipio = municipioPrueba();
        long favorito = inmueble(municipio);
        long descartado = inmueble(municipio);
        swipe(token, favorito, "LIKE");
        swipe(token, descartado, "NOPE");

        String cuerpo = mockMvc.perform(get("/api/v1/swipes/favoritos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(idsDe(cuerpo)).containsExactly(favorito);
    }

    @Test
    void favoritos_cursorDeOtroListado_devuelve400() throws Exception {
        String token = registrarUsuario();
        String municipio = municipioPrueba();
        inmueble(municipio);
        inmueble(municipio);

        String cursorDeck = (String) JsonPath.read(mockMvc.perform(get("/api/v1/swipes/deck")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.nextCursor");
        assertThat(cursorDeck).isNotBlank();

        mockMvc.perform(get("/api/v1/swipes/favoritos")
                        .header("Authorization", "Bearer " + token)
                        .param("cursor", cursorDeck))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cursor"));
    }

    @Test
    void eliminarFavorito_devuelve204YElInmuebleVuelveAlDeck() throws Exception {
        String token = registrarUsuario();
        long usuario = idUsuario(token);
        long inmueble = inmueble(municipioPrueba());
        swipe(token, inmueble, "LIKE");

        mockMvc.perform(delete("/api/v1/swipes/favoritos/{id}", inmueble)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(contarSwipes(usuario, inmueble)).isZero();
        assertThat(idsDeck(token)).contains(inmueble);
    }

    @Test
    void eliminarFavorito_inexistente_devuelve404() throws Exception {
        String token = registrarUsuario();
        long inmueble = inmueble(municipioPrueba());

        mockMvc.perform(delete("/api/v1/swipes/favoritos/{id}", inmueble)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No encontrado"));
    }

    /**
     * IDOR: el usuario B no puede borrar el favorito de A. La query va
     * acotada al {@code usuario_id} del token, así que responde 404 (no 403 y
     * no 204) y la fila de A sigue intacta.
     */
    @Test
    void eliminarFavorito_deOtroUsuario_devuelve404YSigueIntacto() throws Exception {
        String tokenA = registrarUsuario();
        String tokenB = registrarUsuario();
        long usuarioA = idUsuario(tokenA);
        long inmueble = inmueble(municipioPrueba());
        swipe(tokenA, inmueble, "LIKE");

        mockMvc.perform(delete("/api/v1/swipes/favoritos/{id}", inmueble)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("No encontrado"));

        assertThat(contarSwipes(usuarioA, inmueble)).isEqualTo(1);
        assertThat(idsFavoritos(tokenA)).containsExactly(inmueble);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String cuerpo(long inmuebleId, String decision) {
        return """
                {"inmuebleId": %d, "decision": "%s"}""".formatted(inmuebleId, decision);
    }

    private void swipe(String token, long inmuebleId, String decision) throws Exception {
        mockMvc.perform(post("/api/v1/swipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(inmuebleId, decision)))
                .andExpect(status().isCreated());
    }

    private String registrarUsuario() throws Exception {
        String email = "it-swipe-" + UUID.randomUUID() + "@example.com";
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Usuario Swipe","email":"%s","password":"%s"}
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

    private List<Long> idsDeck(String token) throws Exception {
        return idsDe(mockMvc.perform(get("/api/v1/swipes/deck")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private List<Long> idsFavoritos(String token) throws Exception {
        return idsDe(mockMvc.perform(get("/api/v1/swipes/favoritos")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** JsonPath devuelve Integer para enteros pequeños: se normaliza a long. */
    @SuppressWarnings("unchecked")
    private static List<Long> idsDe(String json) {
        List<Number> ids = JsonPath.read(json, "$.content[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private static long idDe(String json, String expresion) {
        return ((Number) JsonPath.read(json, expresion)).longValue();
    }

    private int contarSwipes(long usuario, long inmueble) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM swipes WHERE usuario_id = ? AND inmueble_id = ?",
                Integer.class, usuario, inmueble);
    }

    private String decisionDe(long usuario, long inmueble) {
        return jdbc.queryForObject(
                "SELECT decision FROM swipes WHERE usuario_id = ? AND inmueble_id = ?",
                String.class, usuario, inmueble);
    }

    private String municipioPrueba() {
        return "It-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Swipea (NOPE) todo lo que ya había en {@code inmuebles} para que el deck
     * de este usuario empiece vacío: el anti-join es global, no por municipio,
     * así que sin este aislamiento el test vería también los seeds de dev y
     * fallaría en CI, donde no existen. Las filas se van con el usuario
     * ({@code ON DELETE CASCADE}) en {@link #limpiarFixtures()}.
     */
    private void descartarCatalogoPreexistente(long usuario) {
        jdbc.update("""
                INSERT INTO swipes (usuario_id, inmueble_id, decision)
                SELECT ?, id, 'NOPE' FROM inmuebles
                ON CONFLICT (usuario_id, inmueble_id) DO NOTHING
                """, usuario);
    }

    private long inmueble(String municipio) {
        return jdbc.queryForObject("""
                INSERT INTO inmuebles (titulo, precio, tipo, estado, direccion, municipio,
                                       provincia, lat, lng)
                VALUES ('it-piso de prueba', 200000.00, 'PISO', 'VENTA', 'Calle Test 1', ?,
                        'Madrid', 40.4, -3.7)
                RETURNING id
                """, Long.class, municipio);
    }
}
