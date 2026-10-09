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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.UUID;

/**
 * Casos de uso de autenticación: registro, login email/password, rotación de
 * refresh token, logout y login con Google (vínculo de cuenta + código opaco).
 *
 * <p><b>BCrypt FUERA de la transacción (issue #24).</b> {@code matches} y
 * {@code encode} cuestan 60-150 ms de CPU (cost 10): si corren dentro de un
 * {@code @Transactional} retienen una de las 10 conexiones de Hikari todo ese
 * tiempo y 10 logins concurrentes saturan el pool (el listado público
 * responde 500). El flujo se parte en tres tramos:</p>
 * <ol>
 *   <li><b>lectura corta</b>: la llamada al repositorio abre su propia
 *       transacción de sólo lectura (SimpleJpaRepository) y la cierra al
 *       devolver;</li>
 *   <li><b>BCrypt sin transacción</b>: {@code encode}/{@code matches} corren
 *       con ninguna transacción activa (el hash dummy sigue garantizando el
 *       anti-timing);</li>
 *   <li><b>escritura en transacción NUEVA</b>: alta de usuario + emisión de la
 *       sesión vía {@link TransactionTemplate} (registro) o vía
 *       {@link RefreshTokenService#emitir}, ya {@code @Transactional} (login).</li>
 * </ol>
 * <p>El comportamiento visible es idéntico: mismos códigos, mismos mensajes,
 * misma cookie y misma detección de reuso.</p>
 *
 * <p><b>Sesión revalidada (revisión del PR #54).</b> La emisión recarga al
 * usuario dentro de su transacción: no nace una sesión para una cuenta
 * desactivada en la ventana entre la lectura y el token, y el claim
 * {@code rol} del JWT es el de la fila fresca.</p>
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final CuentaGoogleService cuentaGoogle;

    /**
     * Transacción de escritura del registro: alta de usuario + emisión de la
     * sesión en UNA sola transacción (atómica) y NUNCA abierta mientras se
     * calcula BCrypt. Sólo se usa fuera de transacción, así que con la
     * propagación por defecto ({@code REQUIRED}) siempre crea una nueva.
     */
    private final TransactionTemplate transaccion;

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
                       CuentaGoogleService cuentaGoogle, TransactionTemplate transaccion) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.cuentaGoogle = cuentaGoogle;
        this.transaccion = transaccion;
        this.passwordHashDummy = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Registra un usuario email/password y devuelve su sesión (auto-login).
     *
     * <p>SIN {@code @Transactional} en el método: el {@code encode} BCrypt
     * (60-150 ms de CPU) se calcula ANTES de abrir transacción; sólo el alta y
     * la emisión de la sesión entran en la transacción nueva
     * ({@link TransactionTemplate}), que siguen siendo atómicas entre sí.</p>
     */
    public AuthResult registrar(RegistroCommand comando) {
        String email = normalizarEmail(comando.email());
        if (usuarios.existsByEmail(email)) {
            throw new EmailYaRegistradoException();
        }
        String passwordHash = passwordEncoder.encode(comando.password());
        return transaccion.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setEmail(email);
            usuario.setNombre(comando.nombre().trim());
            usuario.setPasswordHash(passwordHash);
            usuario.setRol(Rol.USER);
            usuario.setActivo(true);
            // emailVerificado queda a false (default de la columna V5): una
            // cuenta local sin verificar NO acepta vínculo Google (guard en
            // Usuario.vincularGoogle). El flujo de verificación de email es un
            // follow-up documentado en el PR.
            usuarios.save(usuario);
            return emitirSesion(usuario);
        });
    }

    /**
     * Login email/password. El mismo mensaje genérico sirve para usuario
     * inexistente, password incorrecta y cuenta sin password (sin enumeración);
     * cuando no hay usuario se compara igualmente contra el hash dummy para
     * que el coste temporal sea idéntico (sin enumeración por timing).
     *
     * <p>SIN {@code @Transactional} en el método (issue #24): la lectura del
     * usuario cierra su transacción de sólo lectura al volver, {@code matches}
     * (BCrypt) corre con NINGUNA transacción activa y la sesión se emite
     * después en la transacción propia de
     * {@link RefreshTokenService#emitir}. Ninguna conexión de Hikari queda
     * retenida durante los 60-150 ms de CPU del hash.</p>
     */
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

    /**
     * Emite la sesión (access + refresh) dentro de su transacción de
     * escritura y con el usuario <b>releído de la BD</b>.
     *
     * <p>Cierra la ventana TOCTOU entre la primera lectura del login y el
     * nacimiento del token: si la cuenta se desactiva (o se borra) en ese
     * intervalo no se emite sesión — se lanza
     * {@link UsuarioInactivoException} (401) o
     * {@link UsuarioNoEncontradoException} (404) — y el claim {@code rol} del
     * JWT sale siempre de la fila fresca, no de una copia ya cacheada.</p>
     *
     * <p>En registro/refresh/canjear el usuario ya está gestionado en la
     * transacción en curso ({@code REQUIRED} la une), así que la relectura no
     * cuesta SQL; sólo el login paga un SELECT por clave primaria. Al entrar
     * en transacción aquí, {@link RefreshTokenService#emitir} se une a ella:
     * relectura, token y refresh se emiten de forma atómica.</p>
     */
    private AuthResult emitirSesion(Usuario usuario) {
        return transaccion.execute(estado -> {
            Usuario vigente = usuarios.findById(usuario.getId())
                    .orElseThrow(UsuarioNoEncontradoException::new);
            if (!vigente.isActivo()) {
                throw new UsuarioInactivoException();
            }
            String access = jwtService.crearAccessToken(vigente);
            String refresh = refreshTokens.emitir(vigente);
            return new AuthResult(access, jwtService.segundosDeExpiracion(), refresh, vigente);
        });
    }

    private static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
