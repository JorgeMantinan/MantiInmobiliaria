package es.manti.profesionales.application;

import es.manti.shared.notificaciones.NotificacionesProperties;

import es.manti.profesionales.domain.ChatVisto;
import es.manti.profesionales.domain.TelegramDiagnosticoPort;
import es.manti.profesionales.domain.TelegramSinCredencialException;
import es.manti.shared.notificaciones.NotificacionesProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Diagnóstico del bot de Telegram: lista los chats que han escrito al bot para
 * conseguir el {@code chat_id} de un grupo y configurar
 * {@code TELEGRAM_CHAT_ID}.
 *
 * <p>Sin transacción (no toca BD): es una llamada HTTP sincrona con timeout de
 * 5 s, al igual que cualquier otra llamada externa.</p>
 */
@Service
@RequiredArgsConstructor
public class DiagnosticoTelegramService {

    private final NotificacionesProperties propiedades;
    private final TelegramDiagnosticoPort diagnostico;

    /**
     * Chats vistos en las últimas actualizaciones del bot.
     *
     * @throws TelegramSinCredencialException 409 si falta
     *         {@code TELEGRAM_BOT_TOKEN}
     */
    public List<ChatVisto> chatsVistos() {
        if (!propiedades.telegram().tieneToken()) {
            throw new TelegramSinCredencialException();
        }
        return diagnostico.chatsVistos();
    }
}
