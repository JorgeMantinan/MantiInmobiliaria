package es.manti.profesionales.domain;

/** Falta {@code TELEGRAM_BOT_TOKEN}: el diagnóstico no puede consultar a Telegram → 409. */
public class TelegramSinCredencialException extends RuntimeException {

    public TelegramSinCredencialException() {
        super("telegram sin credencial: falta TELEGRAM_BOT_TOKEN");
    }
}
