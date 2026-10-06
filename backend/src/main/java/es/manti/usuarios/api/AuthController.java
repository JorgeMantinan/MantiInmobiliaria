package es.manti.usuarios.api;

import es.manti.usuarios.api.dto.AuthMapper;
import es.manti.usuarios.api.dto.AuthResponse;
import es.manti.usuarios.api.dto.LoginRequest;
import es.manti.usuarios.api.dto.OAuth2TokenRequest;
import es.manti.usuarios.api.dto.RegistroRequest;
import es.manti.usuarios.api.dto.UsuarioSummary;
import es.manti.usuarios.application.AuthProperties;
import es.manti.usuarios.application.AuthResult;
import es.manti.usuarios.application.AuthService;
import es.manti.usuarios.application.RegistroCommand;
import es.manti.usuarios.domain.TokenInvalidoException;
import es.manti.usuarios.domain.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import java.time.Duration;

/**
 * Endpoints de autenticación. El refresh token SÓLO viaja en cookie
 * {@code HttpOnly} con path {@code /api/v1/auth}; el access token se devuelve
 * en el body y el cliente lo envía en {@code Authorization: Bearer}.
 */
@Tag(name = "Auth", description = "Registro, login email/password, refresh y login con Google")
@RestController
@RequestMapping("/api/v1/auth")
@Validated
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthMapper mapper;
    private final AuthProperties properties;

    @PostMapping("/register")
    @SecurityRequirements   // endpoint público: security: [] en la spec
    @Operation(summary = "Registra un usuario y le devuelve la sesión (auto-login)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado; sesión emitida"),
            @ApiResponse(responseCode = "400", description = "Validación fallida (ProblemDetail)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Email ya registrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos (rate limit)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<AuthResponse> registrar(@Valid @RequestBody RegistroRequest request) {
        AuthResult result = authService.registrar(
                new RegistroCommand(request.nombre(), request.email(), request.password()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookieSesion(result.refreshToken()).toString())
                .body(mapper.toResponse(result));
    }

    @PostMapping("/login")
    @SecurityRequirements   // endpoint público: security: [] en la spec
    @Operation(summary = "Autentica con email y password")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credenciales válidas; sesión emitida"),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas (ProblemDetail)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos (rate limit)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authService.login(request.email(), request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieSesion(result.refreshToken()).toString())
                .body(mapper.toResponse(result));
    }

    @PostMapping("/refresh")
    @SecurityRequirements   // autenticado por cookie: security: [] en la spec
    @Operation(summary = "Rota el refresh token de la cookie y emite un nuevo access token",
            description = "Requiere la cookie httpOnly de refresh y la cabecera "
                    + "`X-Requested-With: XMLHttpRequest` (protección CSRF elegida).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión renovada"),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido o caducado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Falta la cabecera X-Requested-With",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<AuthResponse> refrescar(HttpServletRequest request) {
        String token = leerCookieSesion(request);
        if (token == null) {
            throw new TokenInvalidoException();
        }
        AuthResult result = authService.refrescar(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieSesion(result.refreshToken()).toString())
                .body(mapper.toResponse(result));
    }

    @PostMapping("/logout")
    @SecurityRequirements   // autenticado por cookie: security: [] en la spec
    @Operation(summary = "Invalida el refresh token y borra la cookie",
            description = "Requiere la cabecera `X-Requested-With: XMLHttpRequest` (protección CSRF elegida).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sesión cerrada (siempre, aunque el token fuera inválido)"),
            @ApiResponse(responseCode = "403", description = "Falta la cabecera X-Requested-With",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> cerrarSesion(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(leerCookieSesion(request));
        // Sesión fantasma: el flujo JWT no necesita JSESSIONID persistente
        // (sólo se crea durante el baile OAuth2 de Google), así que al cerrar
        // la sesión se invalida y se limpia el SecurityContext.
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
        SecurityContextHolder.clearContext();
        response.addHeader(HttpHeaders.SET_COOKIE, cookieBorrada().toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Devuelve el usuario autenticado (access token requerido)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario autenticado"),
            @ApiResponse(responseCode = "401", description = "Sin token o token caducado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<UsuarioSummary> usuarioActual(
            @Parameter(description = "Access token JWT", required = true)
            @AuthenticationPrincipal Jwt jwt) {
        Usuario usuario = authService.obtener(idDesdeToken(jwt));
        return ResponseEntity.ok(mapper.toUsuarioSummary(usuario));
    }

    @PostMapping("/oauth2/token")
    @SecurityRequirements   // endpoint público: security: [] en la spec
    @Operation(summary = "Canjea el código del callback de Google por la sesión",
            description = "Segundo paso del flujo Google: el frontend intercambia aquí el "
                    + "código opaco de un solo uso recibido en /auth/google/callback.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Código válido; sesión emitida"),
            @ApiResponse(responseCode = "400", description = "Código ausente o inválido (ProblemDetail)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Código inválido o caducado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<AuthResponse> canjearCodigoGoogle(
            @Valid @RequestBody OAuth2TokenRequest body) {
        AuthResult result = authService.canjearCodigoGoogle(body.code());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieSesion(result.refreshToken()).toString())
                .body(mapper.toResponse(result));
    }

    // ------------------------------------------------------------------
    // Cookies
    // ------------------------------------------------------------------

    private ResponseCookie cookieSesion(String valor) {
        return ResponseCookie.from(properties.cookie().name(), valor)
                .path(properties.cookie().path())
                .httpOnly(true)
                .secure(properties.cookie().secure())
                .sameSite(properties.cookie().sameSite())
                .maxAge(properties.jwt().refreshTokenTtl())
                .build();
    }

    private ResponseCookie cookieBorrada() {
        return ResponseCookie.from(properties.cookie().name(), "")
                .path(properties.cookie().path())
                .httpOnly(true)
                .secure(properties.cookie().secure())
                .sameSite(properties.cookie().sameSite())
                .maxAge(Duration.ZERO)
                .build();
    }

    private String leerCookieSesion(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, properties.cookie().name());
        return cookie != null ? cookie.getValue() : null;
    }

    private static Long idDesdeToken(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new TokenInvalidoException();
        }
    }
}
