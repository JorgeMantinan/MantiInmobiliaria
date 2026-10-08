package es.manti.profesionales.api;

import es.manti.profesionales.api.dto.DiagnosticoTelegramMapper;
import es.manti.profesionales.api.dto.TelegramDiagnosticoResponse;
import es.manti.profesionales.application.DiagnosticoTelegramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Diagnóstico de la credencial de Telegram (rol ADMIN). Consulta
 * {@code getUpdates} del Bot API para mostrar los chats que han escrito al
 * bot: así se consigue el {@code chat_id} de un grupo y se puede rellenar
 * {@code TELEGRAM_CHAT_ID}.
 *
 * <p>Llamada externa con timeout de 5 s, SIN transacción y SIN salir de la
 * petición hacia el proveedor en ningún otro endpoint.</p>
 */
@Tag(name = "Telegram (admin)",
        description = "Diagnóstico del bot de Telegram: chats que han escrito al bot (requiere rol ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/telegram")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminTelegramController {

    private final DiagnosticoTelegramService service;
    private final DiagnosticoTelegramMapper mapper;

    @GetMapping("/diagnostico")
    @Operation(operationId = "diagnosticoTelegram",
            summary = "Lista los chats que han escrito al bot",
            description = "Llama a `getUpdates` de la Bot API de Telegram (timeout 5 s) y resume los "
                    + "chats vistos: `chatId`, `tipo` (private/group/supergroup/channel) y un extracto "
                    + "del último mensaje. Útil para descubrir el `chat_id` de un grupo y configurar "
                    + "`TELEGRAM_CHAT_ID`.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chats vistos: {chatsVistos: [{chatId, tipo, textoMuestra}]}"),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Token sin rol ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Telegram sin credencial: falta "
                    + "`TELEGRAM_BOT_TOKEN` (ProblemDetail)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<TelegramDiagnosticoResponse> diagnostico() {
        return ResponseEntity.ok(mapper.toResponse(service.chatsVistos()));
    }
}
