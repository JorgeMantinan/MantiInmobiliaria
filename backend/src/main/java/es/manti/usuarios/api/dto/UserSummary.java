package es.manti.usuarios.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Resumen público de un usuario (jamás expone password_hash ni google_id). */
@Schema(description = "Resumen del usuario autenticado")
public record UserSummary(
        @Schema(description = "Identificador", example = "1")
        Long id,
        @Schema(description = "Email", example = "ana@example.com")
        String email,
        @Schema(description = "Nombre visible", example = "Ana Pérez")
        String nombre,
        @Schema(description = "Rol", example = "USER")
        String rol,
        @Schema(description = "Cuenta activa", example = "true")
        boolean activo,
        @Schema(description = "Fecha de alta (ISO-8601)", example = "2026-01-01T10:00:00Z")
        Instant createdAt) {
}
