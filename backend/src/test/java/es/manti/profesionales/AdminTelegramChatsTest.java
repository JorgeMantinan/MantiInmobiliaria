package es.manti.profesionales;

import com.jayway.jsonpath.JsonPath;
import es.manti.profesionales.domain.ChatVisto;
import es.manti.profesionales.domain.NotificacionException;
import es.manti.profesionales.domain.TelegramDiagnosticoPort;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Diagnóstico de Telegram con credencial y PUERTO mockeado: cero red en la
 * suite. Comprueba el contrato {@code {chatsVistos: [{chatId, tipo,
 * textoMuestra}]}}.
 */
@SpringBootTest(properties = "TELEGRAM_BOT_TOKEN=token-de-prueba")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTelegramChatsTest {

    private static final String RUTA = "/api/v1/admin/telegram/diagnostico";
    private static final String PASSWORD = "secreto123";

    @MockitoBean
    private TelegramDiagnosticoPort diagnostico;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void limpiarFixtures() {
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    @Test
    void diagnostico_comoAdmin_devuelve200ConLosChatsVistos() throws Exception {
        when(diagnostico.chatsVistos()).thenReturn(List.of(
                new ChatVisto("-100123456", "supergroup", "Busco un fontanero para mañana"),
                new ChatVisto("555123456", "private", "")));

        mockMvc.perform(get(RUTA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatsVistos").isArray())
                .andExpect(jsonPath("$.chatsVistos[0].chatId").value("-100123456"))
                .andExpect(jsonPath("$.chatsVistos[0].tipo").value("supergroup"))
                .andExpect(jsonPath("$.chatsVistos[0].textoMuestra")
                        .value("Busco un fontanero para mañana"))
                .andExpect(jsonPath("$.chatsVistos[1].tipo").value("private"));

        String cuerpo = mockMvc.perform(get(RUTA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> chats = JsonPath.read(cuerpo, "$.chatsVistos");
        assertThat(chats).hasSize(2);
    }

    @Test
    void diagnostico_conFalloDeTelegram_devuelve502ProblemDetail() throws Exception {
        when(diagnostico.chatsVistos())
                .thenThrow(NotificacionException.reintentable("Telegram no disponible (HTTP 502)"));

        mockMvc.perform(get(RUTA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/notificacion-fallida"))
                .andExpect(jsonPath("$.title").value(
                        "Servicio de notificaciones no disponible"))
                .andExpect(jsonPath("$.detail").value("Telegram no disponible (HTTP 502)"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String tokenAdmin() throws Exception {
        String email = "it-admin-" + UUID.randomUUID() + "@example.com";
        jdbc.update("""
                INSERT INTO usuarios (email, password_hash, nombre, rol, activo)
                VALUES (?, ?, 'Admin Test', 'ADMIN', true)
                """, email, passwordEncoder.encode(PASSWORD));
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.accessToken");
    }
}
