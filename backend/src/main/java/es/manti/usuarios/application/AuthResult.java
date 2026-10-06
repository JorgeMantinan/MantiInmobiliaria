package es.manti.usuarios.application;

import es.manti.usuarios.domain.Usuario;

/**
 * Sesión emitida: access token (body) + refresh token opaco (cookie httpOnly).
 * El mapeo a {@code AuthResponse} lo hace el mapper de {@code api}.
 */
public record AuthResult(String accessToken, long expiresIn, String refreshToken, Usuario usuario) {
}
