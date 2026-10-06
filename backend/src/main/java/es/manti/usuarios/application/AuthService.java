package es.manti.usuarios.application;

import es.manti.usuarios.domain.CodigoInvalidoException;
import es.manti.usuarios.domain.CodigoOAuth2Port;
import es.manti.usuarios.domain.CredencialesInvalidasException;
import es.manti.usuarios.domain.CuentaGoogleConflictivaException;
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
    private final CodigoOAuth2Port codigos;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

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
                       CodigoOAuth2Port codigos, JwtService jwtService,
                       PasswordEncoder passwordEncoder, AuthProperties properties) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.codigos = codigos;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
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
    // Google (OAuth2 + OIDC)
    // ------------------------------------------------------------------

    /**
     * Crea o actualiza la cuenta a partir del perfil de Google y devuelve un
     * código opaco de un solo uso (TTL corto) que el frontend canjeará por la
     * sesión vía {@code POST /api/v1/auth/oauth2/token}.
     */
    @Transactional
    public String crearCodigoGoogle(String googleId, String email, String nombre) {
        String emailNormalizado = normalizarEmail(email);
        Usuario usuario = usuarios.findByGoogleId(googleId)
                .orElseGet(() -> vincularOCrear(googleId, emailNormalizado, nombre));
        if (!usuario.isActivo()) {
            throw new UsuarioInactivoException();
        }
        return codigos.crear(usuario.getId(), properties.oauth2().google().codeTtl());
    }

    /** Canjea el código de un solo uso por la sesión completa (como login). */
    @Transactional
    public AuthResult canjearCodigoGoogle(String codigo) {
        Long usuarioId = codigos.consumir(codigo).orElseThrow(CodigoInvalidoException::new);
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow(CodigoInvalidoException::new);
        if (!usuario.isActivo()) {
            throw new UsuarioInactivoException();
        }
        return emitirSesion(usuario);
    }

    private Usuario vincularOCrear(String googleId, String email, String nombre) {
        return usuarios.findByEmail(email)
                .map(existente -> vincular(existente, googleId, nombre))
                .orElseGet(() -> crearDesdeGoogle(googleId, email, nombre));
    }

    private Usuario vincular(Usuario existente, String googleId, String nombre) {
        if (existente.getGoogleId() != null && !existente.getGoogleId().equals(googleId)) {
            throw new CuentaGoogleConflictivaException();
        }
        existente.setGoogleId(googleId);
        if ((existente.getNombre() == null || existente.getNombre().isBlank())
                && nombre != null && !nombre.isBlank()) {
            existente.setNombre(nombre.trim());
        }
        return usuarios.save(existente);
    }

    private Usuario crearDesdeGoogle(String googleId, String email, String nombre) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setNombre(nombre == null || nombre.isBlank() ? email : nombre.trim());
        usuario.setGoogleId(googleId);
        usuario.setRol(Rol.USER);
        usuario.setActivo(true);
        return usuarios.save(usuario);
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
