package es.manti.usuarios.infra.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Issue #10 — adaptador HIBP (k-anonymity) con el cliente mockeado: la suite
 * NUNCA hace llamadas reales. Vector de referencia: el SHA-1 de "password" es
 * {@code 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8} y por tanto el rango
 * consultado es {@code /range/5BAA6}.
 */
class HibpPasswordAdapterTest {

    /** SHA-1("password") en hex mayúsculas (vector de HIBP). */
    private static final String HASH_PASSWORD = "5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8";

    /** Sufijo de "password" (todo tras el prefijo de 5). */
    private static final String SUFIJO_PASSWORD = "1E4C9B93F3F0682250B6CF8331B7EE68FD8";

    private MockRestServiceServer server;
    private HibpPasswordAdapter adapter;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new HibpPasswordAdapter(builder.build());
    }

    @Test
    void sha1Mayusculas_delVectorPassword_coincideConElDeHibp() {
        assertThat(HibpPasswordAdapter.sha1Mayusculas("password")).isEqualTo(HASH_PASSWORD);
        assertThat(HibpPasswordAdapter.sha1Mayusculas("password"))
                .as("el protocolo k-anonymity exige hex MAYÚSCULAS")
                .isEqualTo(HASH_PASSWORD.toUpperCase());
    }

    @Test
    void estaComprometido_passwordFiltrada_devuelveTrue() {
        server.expect(requestTo(HibpPasswordAdapter.URL_RANGO + "5BAA6"))
                .andRespond(withSuccess(rangoCon(SUFIJO_PASSWORD), MediaType.TEXT_PLAIN));

        assertThat(adapter.estaComprometido("password")).isTrue();
        server.verify();
    }

    @Test
    void estaComprometido_passwordNoFiltrada_devuelveFalse() {
        server.expect(requestTo(HibpPasswordAdapter.URL_RANGO + "5BAA6"))
                .andRespond(withSuccess(rangoOtrosHashes(), MediaType.TEXT_PLAIN));

        assertThat(adapter.estaComprometido("password")).isFalse();
        server.verify();
    }

    @Test
    void estaComprometido_timeoutDeRed_failOpenDevuelveFalse() {
        server.expect(requestTo(HibpPasswordAdapter.URL_RANGO + "5BAA6"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThat(adapter.estaComprometido("password"))
                .as("fail open: un tercero caído no puede bloquear el registro")
                .isFalse();
    }

    @Test
    void estaComprometido_hibpResponde500_failOpenDevuelveFalse() {
        server.expect(requestTo(HibpPasswordAdapter.URL_RANGO + "5BAA6"))
                .andRespond(withServerError());

        assertThat(adapter.estaComprometido("password"))
                .as("fail open también ante 5xx (la rama más probable en prod: 429/5xx de HIBP)")
                .isFalse();
    }

    @Test
    void contieneSufijo_comparaSoloElHashYNoElContador() {
        String cuerpo = "AAAAA:1\n" + SUFIJO_PASSWORD.toLowerCase() + ":36812747\nBBBBB:9";
        assertThat(HibpPasswordAdapter.contieneSufijo(cuerpo, SUFIJO_PASSWORD)).isTrue();
        assertThat(HibpPasswordAdapter.contieneSufijo(cuerpo, "AAAAA")).isTrue();
        assertThat(HibpPasswordAdapter.contieneSufijo(cuerpo, "CCCCC")).isFalse();
    }

    @Test
    void contieneSufijo_cuerpoONulo_sufijoNulo_devuelveFalse() {
        assertThat(HibpPasswordAdapter.contieneSufijo(null, SUFIJO_PASSWORD)).isFalse();
        assertThat(HibpPasswordAdapter.contieneSufijo("", SUFIJO_PASSWORD)).isFalse();
        assertThat(HibpPasswordAdapter.contieneSufijo("AAAAA:1", null)).isFalse();
        assertThat(HibpPasswordAdapter.contieneSufijo("linea-sin-separador", SUFIJO_PASSWORD))
                .as("líneas malformadas se ignoran, no revientan")
                .isFalse();
    }

    private static String rangoCon(String sufijoBuscado) {
        return "0018A45D4D1A03638A291A2659C72344F5F3138B:2\n"
                + sufijoBuscado + ":36812747\n"
                + "FFE072406F917C7C8E8E6F4C5B71AB4B22E7A4D:1\n";
    }

    private static String rangoOtrosHashes() {
        return "0018A45D4D1A03638A291A2659C72344F5F3138B:2\n"
                + "FFE072406F917C7C8E8E6F4C5B71AB4B22E7A4D:1\n";
    }
}
