package es.manti.usuarios.application;

import es.manti.usuarios.domain.CredencialesInvalidasException;
import es.manti.usuarios.domain.EmailYaRegistradoException;
import es.manti.usuarios.domain.Rol;
import es.manti.usuarios.domain.Usuario;
import es.manti.usuarios.domain.UsuarioInactivoException;
import es.manti.usuarios.domain.UsuarioNoEncontradoException;
import es.manti.usuarios.domain.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Casos de uso de autenticación: registro, login email/password, rotación de
 * refresh token, logout y login con Google (vínculo de cuenta + código opaco).
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final CuentaGoogleService cuentaGoogle;

    /**
     * Hash BCrypt de un password inexistente, precalculado UNA sola vez al
     * arrancar: un login con usuario inexistente (o cuenta sin password) paga
     * el mismo coste de CPU que uno real y es indistinguible en tiempo de
     * respuesta (evita la enumeración de emails por timing).
     *
     * <p>Nota: no tiene test unitario fiable — el timing de BCrypt es ruidoso
     * en CI y cualquier umbral daría falsos positivos; se cubre por inspección
     * y por los tests de mensaje genérico ({@code login_conUsuarioInexistente_...}).</p>
     */
    private final String passwordHashDummy;

    public AuthService(UsuarioRepository usuarios, RefreshTokenService refreshTokens,
                       JwtService jwtService, PasswordEncoder passwordEncoder,
                       CuentaGoogleService cuentaGoogle) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.cuentaGoogle = cuentaGoogle;
        this.passwordHashDummy = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Registra un usuario email/password y devuelve su sesión (auto-login). */
    @Transactional
    public AuthResult registrar(RegistroCommand comando) {
        String email = normalizarEmail(comando.email());
        if (usuarios.existsByEmail(email)) {
            throw new EmailYaRegistradoException();
        }
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setNombre(comando.nombre().trim());
        usuario.setPasswordHash(passwordEncoder.encode(comando.password()));
        usuario.setRol(Rol.USER);
        usuario.setActivo(true);
        usuarios.save(usuario);
        return emitirSesion(usuario);
    }

    /**
     * Login email/password. El mismo mensaje genérico sirve para usuario
     * inexistente, password incorrecta y cuenta sin password (sin enumeración);
     * cuando no hay usuario se compara igualmente contra el hash dummy para
     * que el coste temporal sea idéntico (sin enumeración por timing).
     */
    @Transactional
    public AuthResult login(String email, String password) {
        Usuario usuario = usuarios.findByEmail(normalizarEmail(email)).orElse(null);
        if (usuario == null || usuario.getPasswordHash() == null) {
            passwordEncoder.matches(password, passwordHashDummy);
            throw new CredencialesInvalidasException();
        }
        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }
        if (!usuario.isActivo()) {
            throw new UsuarioInactivoException();
        }
        return emitirSesion(usuario);
    }

    /** Rota el refresh token presentado y emite la sesión nueva. */
    @Transactional
    public AuthResult refrescar(String tokenEnClaro) {
        Usuario usuario = refreshTokens.validarYRotar(tokenEnClaro);
        return emitirSesion(usuario);
    }

    /** Invalida el refresh token presentado y no falla si era inexistente. */
    @Transactional
    public void logout(String tokenEnClaro) {
        if (tokenEnClaro != null && !tokenEnClaro.isBlank()) {
            refreshTokens.revocar(tokenEnClaro);
        }
    }

    public Usuario obtener(Long id) {
        return usuarios.findById(id).orElseThrow(UsuarioNoEncontradoException::new);
    }

    // ------------------------------------------------------------------
    // Google (OAuth2 + OIDC): la orquestación vive en CuentaGoogleService
    // ------------------------------------------------------------------

    /** Canjea el código de un solo uso de Google por la sesión completa (como login). */
    @Transactional
    public AuthResult canjearCodigoGoogle(String codigo) {
        return emitirSesion(cuentaGoogle.usuarioPorCodigo(codigo));
    }

    private AuthResult emitirSesion(Usuario usuario) {
        String access = jwtService.crearAccessToken(usuario);
        String refresh = refreshTokens.emitir(usuario);
        return new AuthResult(access, jwtService.segundosDeExpiracion(), refresh, usuario);
    }

    private static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
