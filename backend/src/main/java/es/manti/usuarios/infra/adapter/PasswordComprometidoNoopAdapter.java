package es.manti.usuarios.infra.adapter;

import es.manti.usuarios.domain.PasswordComprometidoPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador no-op del puerto {@link PasswordComprometidoPort}, activo cuando
 * {@code auth.hibp.enabled=false} (perfil de test: la suite NUNCA sale a la
 * red) o como red de seguridad si el adaptador real no se crea por lo que sea.
 * Siempre devuelve {@code false} (deja pasar).
 */
@Component
@ConditionalOnProperty(prefix = "auth.hibp", name = "enabled", havingValue = "false")
public class PasswordComprometidoNoopAdapter implements PasswordComprometidoPort {

    @Override
    public boolean estaComprometido(String password) {
        return false;
    }
}
