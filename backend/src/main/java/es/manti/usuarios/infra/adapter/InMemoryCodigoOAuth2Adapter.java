package es.manti.usuarios.infra.adapter;

import es.manti.usuarios.domain.CodigoOAuth2Port;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación en memoria de los códigos de un solo uso del flujo Google.
 * Limitación documentada: sólo vale con una instancia del backend; con varias
 * instancias habría que pasar este puerto a tabla (o Redis) en la FASE 1.x.
 */
@Component
public class InMemoryCodigoOAuth2Adapter implements CodigoOAuth2Port {

    private record Entrada(Long usuarioId, Instant expiraEn) {
    }

    /** Límite defensivo de códigos vivos antes de forzar limpieza. */
    private static final int LIMPIA_A_PARTIR_DE = 1_000;

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final int BYTES_CODIGO = 32;

    private final Map<String, Entrada> codigos = new ConcurrentHashMap<>();

    @Override
    public String crear(Long usuarioId, Duration ttl) {
        limpiarExpirados();
        String codigo;
        do {
            byte[] bytes = new byte[BYTES_CODIGO];
            ALEATORIO.nextBytes(bytes);
            codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (codigos.containsKey(codigo));
        codigos.put(codigo, new Entrada(usuarioId, Instant.now().plus(ttl)));
        return codigo;
    }

    @Override
    public Optional<Long> consumir(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        Entrada entrada = codigos.remove(codigo);
        if (entrada == null || entrada.expiraEn().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entrada.usuarioId());
    }

    private void limpiarExpirados() {
        if (codigos.size() >= LIMPIA_A_PARTIR_DE) {
            codigos.entrySet().removeIf(e -> e.getValue().expiraEn().isBefore(Instant.now()));
        }
    }
}
