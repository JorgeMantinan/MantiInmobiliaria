package es.manti.usuarios.application;

import es.manti.usuarios.domain.CodigoInvalidoException;
import es.manti.usuarios.domain.CodigoOAuth2Port;
import es.manti.usuarios.domain.Rol;
import es.manti.usuarios.domain.Usuario;
import es.manti.usuarios.domain.UsuarioInactivoException;
import es.manti.usuarios.domain.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Caso de uso de login con Google: vincula (o crea) la cuenta a partir del
 * perfil OIDC y emite el código opaco de un solo uso que el frontend canjeará
 * por la sesión. La lógica de vínculo/validación de conflicto vive en el
 * dominio ({@link Usuario#vincularGoogle}); aquí sólo se orquesta.
 */
@Service
@RequiredArgsConstructor
public class CuentaGoogleService {

    private final UsuarioRepository usuarios;
    private final CodigoOAuth2Port codigos;
    private final AuthProperties properties;

    /**
     * Crea o actualiza la cuenta a partir del perfil de Google y devuelve un
     * código opaco de un solo uso (TTL corto, {@code auth.oauth2.google.code-ttl}).
     */
    @Transactional
    public String crearCodigoGoogle(String googleId, String email, String nombre) {
        String emailNormalizado = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarios.findByGoogleId(googleId)
                .orElseGet(() -> vincularOCrear(googleId, emailNormalizado, nombre));
        if (!usuario.isActivo()) {
            throw new UsuarioInactivoException();
        }
        return codigos.crear(usuario.getId(), properties.oauth2().google().codeTtl());
    }

    /** Canjea el código de un solo uso por el usuario (sesión aparte). */
    @Transactional
    public Usuario usuarioPorCodigo(String codigo) {
        Long usuarioId = codigos.consumir(codigo).orElseThrow(CodigoInvalidoException::new);
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow(CodigoInvalidoException::new);
        if (!usuario.isActivo()) {
            throw new UsuarioInactivoException();
        }
        return usuario;
    }

    private Usuario vincularOCrear(String googleId, String email, String nombre) {
        return usuarios.findByEmail(email)
                .map(existente -> {
                    existente.vincularGoogle(googleId, nombre);
                    return usuarios.save(existente);
                })
                .orElseGet(() -> usuarios.save(nuevoDesdeGoogle(googleId, email, nombre)));
    }

    private static Usuario nuevoDesdeGoogle(String googleId, String email, String nombre) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setNombre(nombre == null || nombre.isBlank() ? email : nombre.trim());
        usuario.setGoogleId(googleId);
        usuario.setRol(Rol.USER);
        usuario.setActivo(true);
        return usuario;
    }
}
