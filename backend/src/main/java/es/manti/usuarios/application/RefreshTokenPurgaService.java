package es.manti.usuarios.application;

import es.manti.usuarios.domain.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purga de refresh tokens caducados (issue #8).
 *
 * <p>La tabla {@code refresh_tokens} crece SIN LÍMITE (una fila por login y
 * otra por cada rotación de refresh) con un TTL de 7 días. Este servicio borra
 * sólo las filas caducadas hace más de {@link #DIAS_RETENCION} días: la
 * retención posterior a la caducidad sirve para auditar reuso de tokens y el
 * borrado aprovecha el índice {@code idx_refresh_tokens_expires_at}
 * (rango sobre {@code expires_at}).</p>
 *
 * <p>La ejecución programada vive en {@link RefreshTokenPurgaJob}
 * (cron diario); aquí sólo está la acción, para poder invocarla a mano y
 * probarla sin el planificador.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenPurgaService {

    /**
     * Días que los tokens expirados se conservan antes de borrarse. El TTL de
     * un refresh es de 7 días, así que 30 días más dan margen holgado para
     * investigar cualquier reuso.
     */
    public static final int DIAS_RETENCION = 30;

    private final RefreshTokenRepository refreshTokens;

    /**
     * Borra los tokens expirados hace más de {@value #DIAS_RETENCION} días.
     *
     * @return número de filas borradas
     */
    @Transactional
    public int purgar() {
        int borrados = refreshTokens.purgarExpirados(DIAS_RETENCION);
        if (borrados > 0) {
            log.info("Purga de refresh tokens: {} fila(s) caducada(s) hace más de {} días borradas",
                    borrados, DIAS_RETENCION);
        }
        return borrados;
    }
}
