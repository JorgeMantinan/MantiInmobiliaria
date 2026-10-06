package es.manti.usuarios.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lógica de vínculo Google del agregado Usuario (sin infraestructura). */
class UsuarioVincularGoogleTest {

    private static Usuario usuario(String googleId, String nombre) {
        Usuario usuario = new Usuario();
        usuario.setEmail("ana@example.com");
        usuario.setNombre(nombre);
        usuario.setGoogleId(googleId);
        usuario.setRol(Rol.USER);
        usuario.setActivo(true);
        return usuario;
    }

    @Test
    void vincularGoogle_enCuentaNueva_seteaElGoogleId() {
        Usuario usuario = usuario(null, "Ana");

        usuario.vincularGoogle("gid-1", "Ana Google");

        assertThat(usuario.getGoogleId()).isEqualTo("gid-1");
        assertThat(usuario.getNombre()).isEqualTo("Ana"); // el nombre propio prevalece
    }

    @Test
    void vincularGoogle_reVinculacionMismoGoogleId_noFalla() {
        Usuario usuario = usuario("gid-1", "Ana");

        usuario.vincularGoogle("gid-1", "Otro Nombre");

        assertThat(usuario.getGoogleId()).isEqualTo("gid-1");
    }

    @Test
    void vincularGoogle_googleIdDistinto_lanzaConflicto() {
        Usuario usuario = usuario("gid-viejo", "Ana");

        assertThatThrownBy(() -> usuario.vincularGoogle("gid-nuevo", "X"))
                .isInstanceOf(CuentaGoogleConflictivaException.class);
        assertThat(usuario.getGoogleId()).isEqualTo("gid-viejo"); // sin cambios
    }

    @Test
    void vincularGoogle_rellenaElNombreSoloSiEstaVacio() {
        Usuario usuario = usuario(null, "  ");

        usuario.vincularGoogle("gid-2", "Nombre De Google");

        assertThat(usuario.getNombre()).isEqualTo("Nombre De Google");
    }
}
