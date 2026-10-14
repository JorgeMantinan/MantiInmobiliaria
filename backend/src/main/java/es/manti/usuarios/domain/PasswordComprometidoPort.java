package es.manti.usuarios.domain;

/**
 * Puerto para comprobar si una contraseña aparece en filtraciones públicas
 * (issue #10). La implementación real ({@code HibpPasswordAdapter}, protocolo
 * k-anonymity de Have I Been Pwned sin API key) vive en {@code infra}; en el
 * perfil de test se usa un adaptador no-op para que la suite nunca salga a la
 * red.
 *
 * <p>El contrato es FAIL OPEN: si el proveedor no responde, el adaptador
 * devuelve {@code false} y deja pasar la contraseña — la disponibilidad del
 * registro no puede depender de un tercero.</p>
 */
public interface PasswordComprometidoPort {

    /**
     * @param password contraseña en claro (nunca se persiste ni se loguea)
     * @return {@code true} si aparece en el rango consultado; {@code false} en
     *         cualquier otro caso (incluido fallo del proveedor)
     */
    boolean estaComprometido(String password);
}
