package es.manti.mercado.application;

import es.manti.mercado.domain.FilaPrecio;
import es.manti.mercado.domain.FuenteMivauPort;
import es.manti.mercado.domain.PrecioMercadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Importador de los dos CSV oficiales del MIVAU.
 *
 * <p><b>Única llamada de red del sistema</b> (RestClient con 30 s de conexión
 * y 60 s de lectura) y SÓLO bajo petición de un ADMIN
 * ({@code POST /api/v1/admin/mercado/actualizar}) o, en el perfil dev, si la
 * tabla está vacía. Ninguna llamada automática en test ni en prod: los ficheros
 * cambian cada año y el endpoint admin es el único responsable de refrescarlos.</p>
 *
 * <p><b>Orden deliberado</b> (skill clean-code-servicios, regla 13): primero se
 * descarga y parsea TODO fuera de transacción —37 MB que no deben ocupar una
 * conexión del pool— y sólo después se escriben los lotes, cada uno en la
 * transacción que abre el repositorio ({@code INSERT ... ON CONFLICT}).</p>
 *
 * <p><b>Upsert por clave natural</b>
 * {@code (codigo_ine, tipo, indicador, tipologia, anio, trimestre)}: repetir la
 * importación no duplica filas, sólo actualiza valor/n_testigos/fecha_descarga.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreciosMercadoImportador {

    private final FuenteMivauPort fuente;
    private final PrecioMercadoRepository repository;
    private final ParserAlquilerMunicipal parserAlquiler;
    private final ParserVentaProvincial parserVenta;

    /**
     * Descarga los dos CSV, los interpreta y hace el upsert.
     *
     * @throws es.manti.mercado.domain.FuenteMivauNoDisponibleException si el CDN
     *         del MIVAU falla o publica una estructura distinta a la esperada
     */
    public ResultadoImportacion importar() {
        Instant fechaDescarga = Instant.now();

        List<FilaPrecio> alquiler = new ArrayList<>();
        fuente.conAlquilerMunicipal(lector -> alquiler.addAll(parserAlquiler.parsear(lector)));

        List<FilaPrecio> venta = new ArrayList<>();
        fuente.conVentaProvincial(lector -> venta.addAll(parserVenta.parsear(lector)));

        int escritasAlquiler = persistir("alquiler municipal", alquiler, fechaDescarga);
        int escritasVenta = persistir("valor tasado provincial", venta, fechaDescarga);

        log.info("Importación MIVAU completada: {} filas de alquiler y {} de valor tasado",
                escritasAlquiler, escritasVenta);
        return new ResultadoImportacion(escritasAlquiler, escritasVenta, fechaDescarga);
    }

    private int persistir(String origen, List<FilaPrecio> filas, Instant fechaDescarga) {
        if (filas.isEmpty()) {
            log.warn("El CSV de {} del MIVAU no ha producido ninguna fila", origen);
            return 0;
        }
        return repository.guardarOActualizar(filas, fechaDescarga);
    }
}
