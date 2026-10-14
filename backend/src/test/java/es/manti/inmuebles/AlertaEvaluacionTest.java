package es.manti.inmuebles;

import es.manti.inmuebles.application.AlertaEvaluacionService;
import es.manti.inmuebles.infra.adapter.AlertaBusquedaJpaRepository;
import es.manti.profesionales.domain.DestinatarioNotificacion;
import es.manti.profesionales.infra.adapter.NotificacionOutboxJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evaluación de las alertas activas (issue #96, endurecida en el follow-up
 * #98): encolado en el outbox COMPARTIDO, marca de agua anti-spam y drenaje en
 * lotes de 10 por alerta.
 *
 * <p>{@code TELEGRAM_CHAT_ID} se fuerza a vacío con una propiedad inline para
 * que el resultado NO dependa del {@code .env} de cada máquina (el camino con
 * chat configurado vive en {@link AlertaEvaluacionTelegramTest}). El job
 * programado NO existe en el perfil de test: se llama al servicio a mano.</p>
 *
 * <p>{@code manti.alertas.lote=1} obliga al barrido a paginar alerta a alerta,
 * ejercitando el bucle de lotes del follow-up #98 en cada test de la clase.</p>
 */
@SpringBootTest(properties = {"TELEGRAM_CHAT_ID=", "manti.alertas.lote=1"})
@ActiveProfiles("test")
class AlertaEvaluacionTest {

    @Autowired
    private AlertaEvaluacionService evaluacion;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AlertaBusquedaJpaRepository alertasJpa;

    @Autowired
    private NotificacionOutboxJpaRepository outboxJpa;

    @AfterEach
    void limpiarFixtures() {
        FixturesAlertas.limpiar(jdbc);
    }

    @Test
    void evaluar_conInmuebleNuevoQueEncaja_encolaEmailYAvanzaLaMarcaDeAgua() {
        String email = "it-alerta-" + fijo() + "@example.com";
        long usuario = FixturesAlertas.usuario(jdbc, email);
        String municipio = "It-alerta-" + fijo();
        long marca = FixturesAlertas.maximoIdInmuebles(jdbc);
        // Nombre con los 5 caracteres especiales del escape HTML: si el cuerpo
        // los filtrara a pelo, el email sería inyectable.
        String nombre = "A<b & \"c\" 'd'>";
        long alerta = FixturesAlertas.alerta(jdbc, usuario, nombre, municipio,
                new BigDecimal("500000"), marca);
        long nuevo = FixturesAlertas.inmueble(jdbc, municipio, new BigDecimal("200000"));

        int avisadas = evaluacion.evaluar();

        assertThat(avisadas).isEqualTo(1);
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT * FROM notificaciones_outbox WHERE alerta_id = ?", alerta);
        assertThat(filas).hasSize(1);
        Map<String, Object> fila = filas.get(0);
        assertThat(fila.get("canal")).isEqualTo("EMAIL");
        assertThat(fila.get("destinatario_tipo")).isEqualTo("USUARIO");
        assertThat(fila.get("destino")).isEqualTo(email);
        assertThat(fila.get("estado")).isEqualTo("PENDIENTE");
        assertThat(fila.get("solicitud_id")).isNull();
        assertThat(fila.get("destinatario_id")).isNull();
        assertThat(fila.get("asunto").toString())
                .contains("1 viviendas nuevas")
                .contains(nombre);

        String cuerpo = fila.get("cuerpo").toString();
        assertThat(cuerpo)
                .contains("It-piso de prueba")
                // Escape HTML del nombre (5 ramas del escapador).
                .contains("A&lt;b &amp; &quot;c&quot; &#39;d&#39;&gt;")
                // Enlace al listado con los MISMOS query params que lee el front.
                .contains("/inmuebles?estado=VENTA")
                .contains("municipio=" + municipio)
                .contains("precioMax=500000");

        // Marca de agua avanzada al mayor id avisado + timestamp de aviso.
        Map<String, Object> despues = jdbc.queryForMap(
                "SELECT ultimo_inmueble_avisado_id, ultimo_aviso_en FROM alertas_busqueda WHERE id = ?",
                alerta);
        assertThat(((Number) despues.get("ultimo_inmueble_avisado_id")).longValue())
                .isEqualTo(nuevo);
        assertThat(despues.get("ultimo_aviso_en")).isNotNull();

        // La fila es legible por el despachador EXISTENTE: cargarla parsea
        // destinatario_tipo='USUARIO' en el enum de profesionales sin fallar.
        long filaId = ((Number) fila.get("id")).longValue();
        assertThat(outboxJpa.findById(filaId))
                .hasValueSatisfying(cargada -> {
                    assertThat(cargada.getDestinatarioTipo())
                            .isEqualTo(DestinatarioNotificacion.USUARIO);
                    assertThat(cargada.getDestino()).isEqualTo(email);
                });
    }

    @Test
    void evaluar_segundaPasadaSinNuevos_noVuelveAAvisar() {
        String email = "it-alerta-" + fijo() + "@example.com";
        long usuario = FixturesAlertas.usuario(jdbc, email);
        String municipio = "It-alerta-" + fijo();
        long marca = FixturesAlertas.maximoIdInmuebles(jdbc);
        long alerta = FixturesAlertas.alerta(jdbc, usuario, "Sin novedad", municipio,
                new BigDecimal("500000"), marca);
        FixturesAlertas.inmueble(jdbc, municipio, new BigDecimal("200000"));

        assertThat(evaluacion.evaluar()).isEqualTo(1);
        // Anti-spam: el mismo inmueble ya está detrás de la marca de agua.
        assertThat(evaluacion.evaluar()).isZero();

        Integer avisos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notificaciones_outbox WHERE alerta_id = ?",
                Integer.class, alerta);
        assertThat(avisos).isEqualTo(1);
    }

    @Test
    void evaluar_inmuebleQueNoEncaja_noEncolaNada() {
        String email = "it-alerta-" + fijo() + "@example.com";
        long usuario = FixturesAlertas.usuario(jdbc, email);
        long marca = FixturesAlertas.maximoIdInmuebles(jdbc);
        long alerta = FixturesAlertas.alerta(jdbc, usuario, "Barata",
                "It-alerta-" + fijo(), new BigDecimal("100000"), marca);
        // Encajaría por municipio, pero el precio supera el precioMax de la alerta.
        FixturesAlertas.inmueble(jdbc,
                jdbc.queryForObject(
                        "SELECT municipio FROM alertas_busqueda WHERE id = ?", String.class, alerta),
                new BigDecimal("200000"));

        assertThat(evaluacion.evaluar()).isZero();

        Integer avisos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notificaciones_outbox WHERE alerta_id = ?",
                Integer.class, alerta);
        assertThat(avisos).isZero();
    }

    @Test
    void evaluar_alertaPausada_noSeEvalua() {
        String email = "it-alerta-" + fijo() + "@example.com";
        long usuario = FixturesAlertas.usuario(jdbc, email);
        String municipio = "It-alerta-" + fijo();
        long marca = FixturesAlertas.maximoIdInmuebles(jdbc);
        long alerta = FixturesAlertas.alerta(jdbc, usuario, "Pausada", municipio,
                new BigDecimal("500000"), marca);
        jdbc.update("UPDATE alertas_busqueda SET activa = false WHERE id = ?", alerta);
        FixturesAlertas.inmueble(jdbc, municipio, new BigDecimal("200000"));

        assertThat(evaluacion.evaluar()).isZero();

        Integer avisos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notificaciones_outbox WHERE alerta_id = ?",
                Integer.class, alerta);
        assertThat(avisos).isZero();
    }

    @Test
    void evaluar_usuarioInactivo_noAvisaNiAvanzaLaMarca() {
        String email = "it-alerta-" + fijo() + "@example.com";
        long usuario = FixturesAlertas.usuario(jdbc, email);
        String municipio = "It-alerta-" + fijo();
        long marca = FixturesAlertas.maximoIdInmuebles(jdbc);
        long alerta = FixturesAlertas.alerta(jdbc, usuario, "Sin destinatario", municipio,
                new BigDecimal("500000"), marca);
        long nuevo = FixturesAlertas.inmueble(jdbc, municipio, new BigDecimal("200000"));
        jdbc.update("UPDATE usuarios SET activo = false WHERE id = ?", usuario);

        assertThat(evaluacion.evaluar()).isZero();

        Integer avisos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notificaciones_outbox WHERE alerta_id = ?",
                Integer.class, alerta);
        assertThat(avisos).isZero();
        // La marca NO avanza: si el usuario reactiva su cuenta, no pierde nada.
        Long marcaDespues = jdbc.queryForObject(
                "SELECT ultimo_inmueble_avisado_id FROM alertas_busqueda WHERE id = ?",
                Long.class, alerta);
        assertThat(marcaDespues).isEqualTo(marca);
        assertThat(nuevo).isGreaterThan(marca);
    }

    @Test
    void evaluar_masDeDiezNuevos_drenaEnLotesDeDiez() {
        String email = "it-alerta-" + fijo() + "@example.com";
        long usuario = FixturesAlertas.usuario(jdbc, email);
        String municipio = "It-alerta-" + fijo();
        long marca = FixturesAlertas.maximoIdInmuebles(jdbc);
        long alerta = FixturesAlertas.alerta(jdbc, usuario, "Lotes", municipio,
                new BigDecimal("500000"), marca);
        long ultima = 0;
        for (int i = 0; i < 12; i++) {
            ultima = FixturesAlertas.inmueble(jdbc, municipio, new BigDecimal("200000"));
        }

        assertThat(evaluacion.evaluar()).isEqualTo(1);
        List<Map<String, Object>> primeras = jdbc.queryForList(
                "SELECT cuerpo FROM notificaciones_outbox WHERE alerta_id = ?", alerta);
        assertThat(primeras).hasSize(1);
        assertThat(contar(primeras.get(0).get("cuerpo").toString(), "<li>")).isEqualTo(10);
        assertThat(marcaActual(alerta)).isLessThan(ultima);

        // Siguiente pasada: drena el resto (2 restantes) sin repetir avisos.
        assertThat(evaluacion.evaluar()).isEqualTo(1);
        List<Map<String, Object>> todas = jdbc.queryForList(
                "SELECT cuerpo FROM notificaciones_outbox WHERE alerta_id = ? ORDER BY id",
                alerta);
        assertThat(todas).hasSize(2);
        assertThat(contar(todas.get(1).get("cuerpo").toString(), "<li>")).isEqualTo(2);
        assertThat(marcaActual(alerta)).isEqualTo(ultima);
    }

    @Test
    void evaluar_maximoIdIgualALaMenorMarca_noBarreNada() {
        String email = "it-alerta-" + fijo() + "@example.com";
        long usuario = FixturesAlertas.usuario(jdbc, email);
        // Alerta cuya marca YA alcanza al mayor inmueble: el cortocircuito del
        // follow-up #98 sale sin encolar ni tocar la alerta.
        long alerta = FixturesAlertas.alerta(jdbc, usuario, "Al día", "It-alerta-" + fijo(),
                new BigDecimal("500000"), FixturesAlertas.maximoIdInmuebles(jdbc));

        assertThat(evaluacion.evaluar()).isZero();

        Integer avisos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notificaciones_outbox WHERE alerta_id = ?",
                Integer.class, alerta);
        assertThat(avisos).isZero();
    }

    @Test
    void evaluar_alertaConMarcaRecienteNoTapaALaAntigua_avisaASoloLaPendiente() {
        // El cortocircuito usa la MENOR marca entre las activas: una alerta
        // reciente (marca al día) no debe tapar a otra antigua con pendientes.
        String emailA = "it-alerta-" + fijo() + "@example.com";
        String emailB = "it-alerta-" + fijo() + "@example.com";
        long usuarioA = FixturesAlertas.usuario(jdbc, emailA);
        long usuarioB = FixturesAlertas.usuario(jdbc, emailB);
        String municipioB = "It-alerta-" + fijo();
        long marcaActual = FixturesAlertas.maximoIdInmuebles(jdbc);
        long pendiente = FixturesAlertas.inmueble(jdbc, municipioB, new BigDecimal("200000"));
        long alertaReciente = FixturesAlertas.alerta(jdbc, usuarioA, "Sin pendientes",
                "It-alerta-" + fijo(), new BigDecimal("500000"), pendiente);
        long alertaAntigua = FixturesAlertas.alerta(jdbc, usuarioB, "Pendiente",
                municipioB, new BigDecimal("500000"), marcaActual);

        assertThat(evaluacion.evaluar()).isEqualTo(1);

        Integer avisosReciente = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notificaciones_outbox WHERE alerta_id = ?",
                Integer.class, alertaReciente);
        assertThat(avisosReciente).isZero();
        Integer avisosAntigua = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notificaciones_outbox WHERE alerta_id = ?",
                Integer.class, alertaAntigua);
        assertThat(avisosAntigua).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private long marcaActual(long alerta) {
        return jdbc.queryForObject(
                "SELECT ultimo_inmueble_avisado_id FROM alertas_busqueda WHERE id = ?",
                Long.class, alerta);
    }

    private static int contar(String texto, String aguja) {
        int total = 0;
        int desde = 0;
        while ((desde = texto.indexOf(aguja, desde)) >= 0) {
            total++;
            desde += aguja.length();
        }
        return total;
    }

    private static String fijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
