package es.manti.inmuebles;

import com.jayway.jsonpath.JsonPath;
import es.manti.inmuebles.application.ConteoInmueblesCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Backoffice de inmuebles: 401 anónimo, 403 usuario sin rol y 201/200/204 con
 * rol ADMIN, más validación Jakarta (ProblemDetail con {@code errors[]}) y
 * soft-delete. Los usuarios de prueba se crean con fixtures propios
 * (prefijo {@code it-}) — nunca dependen del seed de desarrollo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminInmuebleApiTest {

    private static final long ID_INEXISTENTE = 987_654_321L;
    private static final String PASSWORD = "secreto123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ConteoInmueblesCache conteoCache;

    private String tokenAdmin;

    @AfterEach
    void limpiarFixtures() {
        jdbc.update("DELETE FROM inmuebles WHERE municipio LIKE 'It-%'");
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
        // Los fixtures se borran por JDBC (fuera del servicio): se evicta la
        // caché de conteos a mano para que ningún test herede totales viejos.
        conteoCache.invalidarTrasCommit();
    }

    // ------------------------------------------------------------------
    // Autorización
    // ------------------------------------------------------------------

    @Test
    void crear_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/inmuebles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido(municipioPrueba())))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void crear_comoUsuarioNormal_devuelve403() throws Exception {
        String token = registrarUsuario();

        mockMvc.perform(post("/api/v1/admin/inmuebles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido(municipioPrueba())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void eliminar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/inmuebles/{id}", ID_INEXISTENTE))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Issue #27 — el totalElements del listado público se cachea, pero cada
     * escritura del backoffice evicta la entrada tras el commit: no hay
     * ventana de staleness para el usuario que acaba de borrar su anuncio.
     */
    @Test
    void escriturasBackoffice_evictanElTotalCacheadoDelListadoPublico() throws Exception {
        String municipio = municipioPrueba();
        long id = crearComoAdmin(municipio);

        // Primera página: el total (1) queda cacheado bajo la clave del filtro.
        mockMvc.perform(get("/api/v1/inmuebles").param("municipio", municipio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        // Segunda lectura idéntica: sirve la caché con el mismo valor.
        mockMvc.perform(get("/api/v1/inmuebles").param("municipio", municipio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Borrado en backoffice → invalida la entrada; sin la evicción el
        // total seguiría en 1 durante todo el TTL de la caché.
        mockMvc.perform(delete("/api/v1/admin/inmuebles/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/inmuebles").param("municipio", municipio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ------------------------------------------------------------------
    // CRUD con rol ADMIN
    // ------------------------------------------------------------------

    @Test
    void crear_comoAdmin_devuelve201YElInmuebleSeVeEnElDetallePublico() throws Exception {
        String municipio = municipioPrueba();

        String cuerpo = mockMvc.perform(post("/api/v1/admin/inmuebles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido(municipio)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/inmuebles/")))
                .andExpect(jsonPath("$.titulo").value("it-piso de prueba"))
                .andExpect(jsonPath("$.destacado").value(true))
                .andExpect(jsonPath("$.codigoPostal").value("28013"))
                .andReturn().getResponse().getContentAsString();

        long id = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
        mockMvc.perform(get("/api/v1/inmuebles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.municipio").value(municipio));
    }

    @Test
    void crear_conPrecioNoPositivo_devuelve400ConErrors() throws Exception {
        String cuerpo = cuerpoValido(municipioPrueba()).replaceFirst(
                "\"precio\": 245000\\.00", "\"precio\": -10");

        mockMvc.perform(post("/api/v1/admin/inmuebles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.errors[0].field").value("precio"));
    }

    @Test
    void crear_conLatitudFueraDeEspana_devuelve400ConErrors() throws Exception {
        String cuerpo = cuerpoValido(municipioPrueba()).replaceFirst(
                "\"lat\": 40\\.4168", "\"lat\": -55.0");

        mockMvc.perform(post("/api/v1/admin/inmuebles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lat"));
    }

    @Test
    void actualizar_comoAdmin_devuelve200ConLosDatosNuevos() throws Exception {
        String municipio = municipioPrueba();
        long id = crearComoAdmin(municipio);

        String cuerpo = mockMvc.perform(put("/api/v1/admin/inmuebles/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido(municipio)
                                .replace("it-piso de prueba", "it-piso reformado")
                                .replace("\"precio\": 245000.00", "\"precio\": 310000.00")
                                .replace("\"estado\": \"VENTA\"", "\"estado\": \"ALQUILER\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("it-piso reformado"))
                .andExpect(jsonPath("$.estado").value("ALQUILER"))
                .andReturn().getResponse().getContentAsString();

        assertThat(((Number) JsonPath.read(cuerpo, "$.id")).longValue()).isEqualTo(id);
        Object precio = JsonPath.read(cuerpo, "$.precio");
        assertThat(new BigDecimal(String.valueOf(precio))).isEqualByComparingTo("310000");

        mockMvc.perform(get("/api/v1/inmuebles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("it-piso reformado"));
    }

    @Test
    void actualizar_inexistente_comoAdmin_devuelve404() throws Exception {
        mockMvc.perform(put("/api/v1/admin/inmuebles/{id}", ID_INEXISTENTE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido(municipioPrueba())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("No encontrado"));
    }

    @Test
    void eliminar_comoAdmin_devuelve204YDesapareceDeLaApiPublica() throws Exception {
        String municipio = municipioPrueba();
        long id = crearComoAdmin(municipio);

        mockMvc.perform(delete("/api/v1/admin/inmuebles/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/inmuebles/" + id))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/inmuebles").param("municipio", municipio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        // La fila se conserva (soft-delete), sólo sale de la API pública
        Integer filas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inmuebles WHERE id = ?", Integer.class, id);
        assertThat(filas).isEqualTo(1);
    }

    @Test
    void eliminar_inexistente_comoAdmin_devuelve404() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/inmuebles/{id}", ID_INEXISTENTE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("No encontrado"));
    }

    // ------------------------------------------------------------------
    // Soft-delete: reintentar una escritura sobre un borrado -> 409
    // ------------------------------------------------------------------

    @Test
    void eliminar_porSegundaVez_comoAdmin_devuelve409() throws Exception {
        String municipio = municipioPrueba();
        long id = crearComoAdmin(municipio);

        mockMvc.perform(delete("/api/v1/admin/inmuebles/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/admin/inmuebles/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Inmueble ya eliminado"))
                .andExpect(jsonPath("$.detail").value("Inmueble " + id + " ya eliminado"));

        // Sigue borrado: una sola fila y fuera de la API pública
        Integer filas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inmuebles WHERE id = ?", Integer.class, id);
        assertThat(filas).isEqualTo(1);
        mockMvc.perform(get("/api/v1/inmuebles/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void actualizar_yaEliminado_comoAdmin_devuelve409() throws Exception {
        String municipio = municipioPrueba();
        long id = crearComoAdmin(municipio);

        mockMvc.perform(delete("/api/v1/admin/inmuebles/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/v1/admin/inmuebles/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido(municipio)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Inmueble ya eliminado"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String municipioPrueba() {
        return "It-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private long crearComoAdmin(String municipio) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/admin/inmuebles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido(municipio)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    /** Crea (una vez por clase) un usuario ADMIN con fixtures propios y hace login. */
    private String tokenAdmin() throws Exception {
        if (tokenAdmin == null) {
            String email = "it-admin-" + UUID.randomUUID() + "@example.com";
            jdbc.update("""
                    INSERT INTO usuarios (email, password_hash, nombre, rol, activo)
                    VALUES (?, ?, 'Admin Test', 'ADMIN', true)
                    """, email, passwordEncoder.encode(PASSWORD));
            tokenAdmin = login(email);
        }
        return tokenAdmin;
    }

    private String registrarUsuario() throws Exception {
        String email = "it-user-" + UUID.randomUUID() + "@example.com";
        return JsonPath.read(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Usuario Test","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private String login(String email) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String cuerpoValido(String municipio) {
        return """
                {
                  "titulo": "it-piso de prueba",
                  "descripcion": "Descripcion de prueba del backoffice",
                  "precio": 245000.00,
                  "superficieM2": 90,
                  "habitaciones": 3,
                  "banios": 1,
                  "tipo": "PISO",
                  "estado": "VENTA",
                  "direccion": "Calle Mayor 1",
                  "codigoPostal": "28013",
                  "municipio": "%s",
                  "provincia": "Madrid",
                  "lat": 40.4168,
                  "lng": -3.7038,
                  "imagenUrl": "https://picsum.photos/seed/admin/800/600",
                  "destacado": true
                }
                """.formatted(municipio);
    }
}
