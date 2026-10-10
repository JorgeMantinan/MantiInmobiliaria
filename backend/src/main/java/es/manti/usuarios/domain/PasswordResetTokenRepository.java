package es.manti.usuarios.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Puerto de persistencia de los tokens de restablecimiento de contraseña.
 * Implementado en infra por Spring Data JPA.
 */
public interface PasswordResetTokenRepository {

    /**
     * Busca el token por su hash SHA-256 con el usuario YA resuelto
     * (la asociación es perezosa: la query trae ambos en un solo SELECT).
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    PasswordResetToken save(PasswordResetToken token);

    /**
     * Canje atómico del token: rellena {@code usado_en} sólo si sigue vigente
     * (sin usar y sin caducar). Devuelve el nº de filas afectadas —
     * {@code 0} significa token ya canjeado o caducado entre la lectura y la
     * escritura (carrera entre dos peticiones), lo que se traduce en 400.
     *
     * @param id    identificador del token
     * @param ahora instante del canje (se usa también para comprobar caducidad)
     * @return 1 si el canje ganó la carrera, 0 en caso contrario
     */
    int consumir(Long id, Instant ahora);

    /**
     * ¿Existe ya un token VIGENTE (sin canjear y sin caducar) para el
     * usuario? Lo usa el cooldown de {@code forgot} (revisión del PR #64):
     * si el usuario pide el enlace otra vez con uno vivo, no se emite otro.
     *
     * @param usuarioId identificador del usuario
     * @param ahora     instante de la comprobación
     * @return true si hay un token vigente
     */
    boolean existeVigentePara(Long usuarioId, Instant ahora);

    /**
     * Borra TODOS los tokens del usuario (vigentes y caducados) en una única
     * sentencia: al emitir uno nuevo queda un solo enlace vivo por cuenta.
     *
     * @return nº de filas borradas
     */
    int eliminarTodosDeUsuario(Long usuarioId);
}
