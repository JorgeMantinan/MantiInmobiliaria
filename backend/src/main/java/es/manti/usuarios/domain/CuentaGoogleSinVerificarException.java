package es.manti.usuarios.domain;

/**
 * Cuenta local cuyo email nunca se verificó: no acepta vínculo con Google.
 *
 * <p>Cierra el pre-hijack de cuentas (ATO): sin este guard, registrarse con el
 * email de otra persona y esperar a que esa persona entre con Google basta
 * para quedar vinculado a su identidad. Se responde 409
 * ({@code account_conflict} en {@code UsuarioErrorAdvice}) y, en el flujo
 * OAuth2, con el redirect {@code ?error=account_conflict} que el frontend ya
 * entiende.</p>
 */
public class CuentaGoogleSinVerificarException extends AuthException {

    public CuentaGoogleSinVerificarException() {
        super("La cuenta asociada a este email no está verificada; no se puede vincular con Google");
    }
}
