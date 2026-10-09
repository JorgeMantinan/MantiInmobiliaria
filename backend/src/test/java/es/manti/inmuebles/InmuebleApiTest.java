package es.manti.inmuebles;

import com.jayway.jsonpath.JsonPath;
import es.manti.inmuebles.domain.EstadoInmueble;
import es.manti.inmuebles.domain.Inmueble;
import es.manti.inmuebles.domain.InmuebleDatos;
import es.manti.inmuebles.domain.InmuebleRepository;
import es.manti.inmuebles.domain.TipoInmueble;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API pública de inmuebles con MockMvc contra PostgreSQL real: caché, filtros,
 * paginación por cursor (primera/segunda/fin), cursor inválido y soft-delete.
 * Sin autenticación en todos los casos (el portal es anónimo).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InmuebleApiTest {

    private static final long ID_INEXISTENTE = 987_654_321L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InmuebleRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiarFixtures() {
        jdbc.update("DELETE FROM inmuebles WHERE municipio LIKE 'It-%'");
    }

    // ------------------------------------------------------------------
    // Listado
    // ------------------------------------------------------------------

    @Test
    void listado_sinAutenticacion_devuelve200ConCachePublico() throws Exception {
        // Municipio inexistente a propósito: la tabla comparte datos con el seed
        // de dev, así que el "vacío" se garantiza con el filtro, no con el estado.
        mockMvc.perform(get("/api/v1/inmuebles").param("municipio", municipioPrueba()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        allOf(containsString("public"), containsString("max-age=60"))))
                // La respuesta depende del origen (CORS con allowlist): el caché
                // no puede reutilizarla entre orígenes distintos.
                .andExpect(header().string(HttpHeaders.VARY, containsString("Origin")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(0)))
                // Sin filas no hay página siguiente: el campo existe con valor null
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listado_primeraPagina_devuelveCursorTotalYFilas() throws Exception {
        String municipio = municipioPrueba();
        crear(municipio, "it-uno", "100000");
        crear(municipio, "it-dos", "200000");
        crear(municipio, "it-tres", "300000");

        String cuerpo = mockMvc.perform(get("/api/v1/inmuebles")
                        .param("municipio", municipio)
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(cuerpo, "$.nextCursor")).isNotBlank();
    }

    @Test
    void listado_cursorDevuelveTodasLasFilasSinRepetirYNuloAlFinal() throws Exception {
        String municipio = municipioPrueba();
        for (int i = 1; i <= 7; i++) {
            crear(municipio, "it-" + i, String.valueOf(100000 + i * 1000));
        }

        String pagina1 = pagina(municipio, null, "3");
        List<Long> ids = new ArrayList<>(idsDe(pagina1));
        assertThat(ids).hasSize(3);
        String cursor1 = (String) JsonPath.read(pagina1, "$.nextCursor");
        assertThat(cursor1).isNotBlank();
        assertThat(((Number) JsonPath.read(pagina1, "$.totalElements")).intValue()).isEqualTo(7);

        String pagina2 = pagina(municipio, cursor1, "3");
        ids.addAll(idsDe(pagina2));
        String cursor2 = (String) JsonPath.read(pagina2, "$.nextCursor");
        assertThat(cursor2).isNotBlank();
        // totalElements sólo en la primera página (sin COUNT por cada salto)
        Object totalPagina2 = JsonPath.read(pagina2, "$.totalElements");
        assertThat(totalPagina2).isNull();

        String pagina3 = pagina(municipio, cursor2, "3");
        ids.addAll(idsDe(pagina3));
        assertThat((String) JsonPath.read(pagina3, "$.nextCursor")).isNull();

        assertThat(ids).hasSize(7).doesNotHaveDuplicates();
    }

    @Test
    void listado_cursorCorrupto_devuelve400ConProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/inmuebles")
                        .param("municipio", municipioPrueba())
                        .param("cursor", "dGV4dG9xdWUtbm9jdXJzbw"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Parámetro inválido"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void listado_cursorDeOtroOrden_devuelve400() throws Exception {
        String municipio = municipioPrueba();
        for (int i = 1; i <= 5; i++) {
            crear(municipio, "it-" + i, String.valueOf(100000 + i));
        }
        String cuerpo = pagina(municipio, null, "3");
        String cursorCreado = (String) JsonPath.read(cuerpo, "$.nextCursor");
        assertThat(cursorCreado).isNotBlank();

        // Un cursor emitido por fecha no sirve si ahora se pide orden por precio.
        mockMvc.perform(get("/api/v1/inmuebles")
                        .param("municipio", municipio)
                        .param("sort", "precio_asc")
                        .param("cursor", cursorCreado))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"));
    }

    @Test
    void listado_sortNoAdmitido_devuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/inmuebles").param("sort", "fecha"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"));
    }

    @Test
    void listado_conSortPrecioDesc_devuelvePreciosDescendentes() throws Exception {
        String municipio = municipioPrueba();
        crear(municipio, "it-barato", "150000");
        crear(municipio, "it-caro", "350000");
        crear(municipio, "it-medio", "250000");

        String cuerpo = mockMvc.perform(get("/api/v1/inmuebles")
                        .param("municipio", municipio)
                        .param("sort", "precio_desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andReturn().getResponse().getContentAsString();

        List<BigDecimal> precios = preciosDe(cuerpo);
        for (int i = 1; i < precios.size(); i++) {
            assertThat(precios.get(i - 1)).isGreaterThanOrEqualTo(precios.get(i));
        }
        assertThat(precios.get(0)).isEqualByComparingTo("350000");
        assertThat(precios.get(2)).isEqualByComparingTo("150000");
    }

    @Test
    void listado_limiteSuperiorA50_seAcotaACincuenta() throws Exception {
        String municipio = municipioPrueba();
        List<Object[]> filas = new ArrayList<>();
        for (int i = 1; i <= 51; i++) {
            filas.add(new Object[]{"it-lote-" + i, 100000 + i, "PISO", "VENTA",
                    "Calle Lote 1", municipio, "Madrid", 40.4, -3.7});
        }
        jdbc.batchUpdate("""
                INSERT INTO inmuebles (titulo, precio, tipo, estado, direccion, municipio, provincia, lat, lng)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, filas);

        mockMvc.perform(get("/api/v1/inmuebles")
                        .param("municipio", municipio)
                        .param("limit", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(50)))
                .andExpect(jsonPath("$.totalElements").value(51))
                .andExpect(jsonPath("$.nextCursor").isString());
    }

    @Test
    void listado_conFiltrosEstadoYMunicipioParcial_esCaseInsensitive() throws Exception {
        String fragmento = UUID.randomUUID().toString().substring(0, 8);
        String municipio = "It-" + fragmento + "-Alcala";
        crear(municipio, "it-venta", "200000", EstadoInmueble.VENTA, TipoInmueble.PISO, true);
        crear(municipio, "it-alquiler", "900", EstadoInmueble.ALQUILER, TipoInmueble.PISO, false);

        // Case-insensitive: el municipio se envía entero en mayúsculas.
        mockMvc.perform(get("/api/v1/inmuebles")
                        .param("municipio", municipio.toUpperCase())
                        .param("estado", "VENTA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].estado").value("VENTA"))
                .andExpect(jsonPath("$.content[0].titulo").value("it-venta"));

        // Parcial: sólo un fragmento intermedio del municipio.
        String cuerpo = mockMvc.perform(get("/api/v1/inmuebles")
                        .param("municipio", fragmento)
                        .param("tipo", "PISO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(cuerpo, "$.totalElements")).intValue()).isEqualTo(2);
    }

    /**
     * Regresión hotfix listado sin filtros: con ningún query param el patrón de
     * municipio es NULL y Hibernate lo bindea como JAVA_OBJECT (null → bytea en
     * pgjdbc), lo que rompía {@code LOWER(:p)} con
     * {@code function lower(bytea) does not exist} → 500 en producción.
     */
    @Test
    void listado_sinFiltros_devuelve200ConResultados() throws Exception {
        String municipio = municipioPrueba();
        crear(municipio, "it-regresion-uno", "100000");
        crear(municipio, "it-regresion-dos", "200000");
        // El total esperado se calcula contra la MISMA BD en este instante: el
        // esquema de test comparte datos con el seed de dev.
        Integer esperado = jdbc.queryForObject(
                "SELECT count(*) FROM inmuebles WHERE eliminado_en IS NULL", Integer.class);

        String cuerpo = mockMvc.perform(get("/api/v1/inmuebles"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", not(empty())))
                .andExpect(jsonPath("$.totalElements").value(esperado))
                .andReturn().getResponse().getContentAsString();

        // Nuestras fixtures son las más recientes: caben en la primera página.
        List<String> titulos = JsonPath.read(cuerpo, "$.content[*].titulo");
        assertThat(titulos).contains("it-regresion-uno", "it-regresion-dos");
    }

    /**
     * El patrón llega a JDBC ya en minúsculas (toLowerCase del servicio); con el
     * parámetro pre-plegado la query sigue siendo case-insensitive.
     */
    @Test
    void listado_sinFiltros_caseInsensitive() throws Exception {
        String municipio = "It-" + UUID.randomUUID().toString().substring(0, 8) + "-madrid";
        Inmueble creado = crear(municipio, "it-case-insensitive", "175000");

        String cuerpo = mockMvc.perform(get("/api/v1/inmuebles").param("municipio", "MADRid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", not(empty())))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andReturn().getResponse().getContentAsString();

        assertThat(idsDe(cuerpo)).contains(creado.getId());
        assertThat(((Number) JsonPath.read(cuerpo, "$.totalElements")).intValue()).isPositive();
    }

    @Test
    void listado_noIncluyeInmueblesEliminados() throws Exception {
        String municipio = municipioPrueba();
        Inmueble borrado = crear(municipio, "it-borrado", "100000");
        Inmueble vigente = crear(municipio, "it-vigente", "100000");
        borrado.eliminar();
        repository.guardar(borrado);

        String cuerpo = mockMvc.perform(get("/api/v1/inmuebles").param("municipio", municipio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        assertThat(idsDe(cuerpo)).containsExactly(vigente.getId());
    }

    // ------------------------------------------------------------------
    // Validación de parámetros de búsqueda (400 con errors[])
    // ------------------------------------------------------------------

    @Test
    void listado_precioMinMayorQuePrecioMax_devuelve400ConErrors() throws Exception {
        mockMvc.perform(get("/api/v1/inmuebles")
                        .param("precioMin", "500000")
                        .param("precioMax", "100000"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Parámetro inválido"))
                .andExpect(jsonPath("$.errors[0].field").value("precioMin"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void listado_precioNegativo_devuelve400ConErrors() throws Exception {
        mockMvc.perform(get("/api/v1/inmuebles").param("precioMin", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("precioMin"));
    }

    @Test
    void listado_habitacionesNegativas_devuelve400ConErrors() throws Exception {
        mockMvc.perform(get("/api/v1/inmuebles").param("habitacionesMin", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("habitacionesMin"));
    }

    @Test
    void listado_estadoNoAdmitido_devuelve400ConErrors() throws Exception {
        mockMvc.perform(get("/api/v1/inmuebles").param("estado", "XYZ"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Parámetro inválido"))
                .andExpect(jsonPath("$.errors[0].field").value("estado"));
    }

    @Test
    void listado_limitNoNumerico_devuelve400ConErrors() throws Exception {
        mockMvc.perform(get("/api/v1/inmuebles").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
    }

    @Test
    void listado_municipioMasLargoDe120_devuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/inmuebles").param("municipio", "x".repeat(121)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validación fallida"));
    }

    // ------------------------------------------------------------------
    // Seguridad: el recurso público sólo acepta GET anónimo
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0} {1} sin token -> 401")
    @CsvSource({
            "POST,   /api/v1/inmuebles",
            "PUT,    /api/v1/inmuebles/1",
            "DELETE, /api/v1/inmuebles/1",
            "DELETE, /api/v1/inmuebles"
    })
    void escrituras_sinToken_devuelven401(String metodo, String ruta) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(metodo), URI.create(ruta))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * El matcher público es {@code {id:[0-9]+}}: cualquier otro segmento bajo
     * {@code /api/v1/inmuebles/} deja de estar abierto y cae en 401 en vez de
     * devolver un 404 de la aplicación.
     */
    @Test
    void detalle_idNoNumerico_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/inmuebles/no-es-un-id"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Detalle
    // ------------------------------------------------------------------

    @Test
    void detalle_existente_devuelve200ConCachePublico() throws Exception {
        String municipio = municipioPrueba();
        Inmueble inmueble = crear(municipio, "it-piso", "245000");

        String cuerpo = mockMvc.perform(get("/api/v1/inmuebles/" + inmueble.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        allOf(containsString("public"), containsString("max-age=60"))))
                .andExpect(header().string(HttpHeaders.VARY, containsString("Origin")))
                .andExpect(jsonPath("$.descripcion").value("Descripción de prueba del listado"))
                .andExpect(jsonPath("$.direccion").value("Calle de prueba 1"))
                .andReturn().getResponse().getContentAsString();

        assertThat(((Number) JsonPath.read(cuerpo, "$.id")).longValue())
                .isEqualTo(inmueble.getId());
        Object precio = JsonPath.read(cuerpo, "$.precio");
        assertThat(new BigDecimal(String.valueOf(precio))).isEqualByComparingTo("245000");
    }

    @Test
    void detalle_inexistente_devuelve404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/inmuebles/" + ID_INEXISTENTE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No encontrado"))
                .andExpect(jsonPath("$.detail").value("Inmueble " + ID_INEXISTENTE + " inexistente"));
    }

    @Test
    void detalle_inmuebleEliminado_devuelve404() throws Exception {
        String municipio = municipioPrueba();
        Inmueble borrado = crear(municipio, "it-borrado", "100000");
        borrado.eliminar();
        repository.guardar(borrado);

        mockMvc.perform(get("/api/v1/inmuebles/" + borrado.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("No encontrado"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String municipioPrueba() {
        return "It-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String pagina(String municipio, String cursor, String limit) throws Exception {
        var peticion = get("/api/v1/inmuebles").param("municipio", municipio).param("limit", limit);
        if (cursor != null) {
            peticion = peticion.param("cursor", cursor);
        }
        MvcResult resultado = mockMvc.perform(peticion)
                .andExpect(status().isOk())
                .andReturn();
        return resultado.getResponse().getContentAsString();
    }

    @SuppressWarnings("unchecked")
    private static List<Long> idsDe(String json) {
        List<Number> ids = JsonPath.read(json, "$.content[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<BigDecimal> preciosDe(String json) {
        List<Number> precios = JsonPath.read(json, "$.content[*].precio");
        return precios.stream().map(p -> new BigDecimal(String.valueOf(p))).toList();
    }

    private Inmueble crear(String municipio, String titulo, String precio) {
        return crear(municipio, titulo, precio, EstadoInmueble.VENTA, TipoInmueble.PISO, false);
    }

    private Inmueble crear(String municipio, String titulo, String precio,
                           EstadoInmueble estado, TipoInmueble tipo, boolean destacado) {
        InmuebleDatos datos = new InmuebleDatos(
                titulo, "Descripción de prueba del listado", new BigDecimal(precio), 90,
                (short) 2, (short) 1, tipo, estado, "Calle de prueba 1", "28001",
                municipio, "Madrid", 40.4, -3.7, "https://picsum.photos/seed/it/800/600", destacado);
        return repository.guardar(Inmueble.nuevo(datos));
    }
}
