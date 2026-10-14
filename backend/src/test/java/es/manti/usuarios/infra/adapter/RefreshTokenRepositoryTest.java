package es.manti.usuarios.infra.adapter;

import es.manti.usuarios.domain.RefreshToken;
import es.manti.usuarios.domain.RefreshTokenRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #31 — higiene de {@code refresh_tokens} en el repositorio JPA:
 *
 * <ul>
 *   <li>{@code findByTokenHash} con {@code JOIN FETCH} del usuario: el refresh
 *       se lleva UN SELECT en vez de dos (el segundo era el lazy load del
 *       usuario que {@code validarYRotar} lee siempre);</li>
 *   <li>{@code revocarTodosDeUsuario} con {@code CURRENT_TIMESTAMP}: un UPDATE
 *       por JPQL no dispara {@code @UpdateTimestamp}, así que la revocación en
 *       masa dejaba {@code updated_at} en la fecha del login original.</li>
 * </ul>
 *
 * <p>Contra la BD real del perfil de test (manti_test), con datos propios y
 * limpieza en {@code @AfterEach}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private String email;

    @BeforeEach
    void crearUsuarioDePrueba() {
        email = "it-refresh-repo-" + UUID.randomUUID() + "@example.com";
        // password_hash NO NULL (ck_usuarios_credencial): este usuario nunca
        // hace login, sólo sostiene refresh tokens.
        jdbc.update("INSERT INTO usuarios (email, nombre, password_hash, rol, activo) "
                + "VALUES (?, ?, 'hash-de-prueba', 'USER', true)", email, "Repo Test");
    }

    @AfterEach
    void limpiar() {
        // Los refresh_tokens caen en cascada (fk ON DELETE CASCADE).
        jdbc.update("DELETE FROM usuarios WHERE email = ?", email);
    }

    @Test
    void findByTokenHash_cargaElUsuarioEnLaMismaConsulta() {
        String tokenHash = insertarToken("fetch");

        RefreshToken token = repository.findByTokenHash(tokenHash).orElseThrow();

        // Sin JOIN FETCH el ManyToOne(LAZY) devuelve un proxy: al estar la
        // entidad desenganchada de la sesión (la query corre en SU transacción),
        // leer el usuario saltería LazyInitializationException. Con el fetch
        // llega inicializado en el mismo SELECT.
        assertThat(Hibernate.isInitialized(token.getUsuario()))
                .as("el usuario debe venir inicializado (JOIN FETCH), no como proxy")
                .isTrue();
        assertThat(token.getUsuario().getEmail()).isEqualTo(email);
        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    void revocarTodosDeUsuario_tambienActualizaElUpdatedAt() {
        String tokenHash = insertarToken("revocar");
        Instant antiguo = Instant.parse("2000-01-01T00:00:00Z");
        jdbc.update("UPDATE refresh_tokens SET updated_at = ?::timestamptz WHERE token_hash = ?",
                antiguo.toString(), tokenHash);

        long revocados = repository.revokeAllFromUsuario(usuarioId());

        assertThat(revocados).isEqualTo(1);
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT revoked, updated_at FROM refresh_tokens WHERE token_hash = ?", tokenHash);
        assertThat(fila.get("revoked")).as("token revocado").isEqualTo(Boolean.TRUE);
        assertThat(((java.sql.Timestamp) fila.get("updated_at")).toInstant())
                .as("updated_at debe refrescarse con la revocación (CURRENT_TIMESTAMP)")
                .isAfter(antiguo);
    }

    /** Segunda pasada: sólo cuenta los que seguían activos y sigue idempotente. */
    @Test
    void revocarTodosDeUsuario_conAlgunoYaRevocado_noLosVuelveAContar() {
        String activo = insertarToken("activo");
        String yaRevocado = insertarToken("ya-revocado");
        jdbc.update("UPDATE refresh_tokens SET revoked = true WHERE token_hash = ?", yaRevocado);

        long revocados = repository.revokeAllFromUsuario(usuarioId());

        assertThat(revocados).isEqualTo(1);
        assertThat(contarRevocados()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String insertarToken(String marca) {
        String base = (marca + "-" + UUID.randomUUID()).replace("-", "");
        String tokenHash = (base + base).substring(0, 64);
        jdbc.update("""
                        INSERT INTO refresh_tokens (token_hash, usuario_id, expires_at, revoked)
                        VALUES (?, (SELECT id FROM usuarios WHERE email = ?), ?::timestamptz, false)
                        """,
                tokenHash, email, Instant.now().plus(Duration.ofDays(5)).toString());
        return tokenHash;
    }

    private long usuarioId() {
        return jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", Long.class, email);
    }

    private int contarRevocados() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens t JOIN usuarios u ON u.id = t.usuario_id "
                        + "WHERE u.email = ? AND t.revoked", Integer.class, email);
    }
}
