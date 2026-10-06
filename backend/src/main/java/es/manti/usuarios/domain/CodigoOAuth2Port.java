package es.manti.usuarios.domain;

import java.time.Duration;
import java.util.Optional;

/**
 * Puerto del código de un solo uso que recibe el frontend tras el callback
 * de Google. Implementación actual: en memoria (una sola instancia).
 */
public interface CodigoOAuth2Port {

    /**
     * Genera y almacena un código opaco para el usuario indicado.
     *
     * @return código opaco (URL-safe Base64)
     */
    String crear(Long usuarioId, Duration ttl);

    /**
     * Consume el código (un solo uso): si es válido y no ha caducado,
     * lo elimina y devuelve el id del usuario.
     */
    Optional<Long> consumir(String codigo);
}
