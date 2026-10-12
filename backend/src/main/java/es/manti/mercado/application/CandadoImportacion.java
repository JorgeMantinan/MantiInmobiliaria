package es.manti.mercado.application;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * Exclusión mutua de las importaciones de precios del MIVAU: el endpoint admin
 * es único y una pasada descarga 37 MB, así que sólo se admite UNA a la vez.
 *
 * <p>Vive en {@code application} (no en el controller) para poder testear el
 * candado sin una carrera real de hilos: el test lo toma a mano y comprueba que
 * el segundo llamante recibe 409.</p>
 */
@Component
public class CandadoImportacion {

    private final Semaphore candado = new Semaphore(1);

    /**
     * @return {@code true} si ha entrado; {@code false} si ya hay una
     *         importación en curso (el caller debe responder 409)
     */
    public boolean intentarEntrar() {
        return candado.tryAcquire();
    }

    /** Libera la exclusión mutua; siempre en {@code finally}. */
    public void salir() {
        candado.release();
    }

    /** Visible para tests: comprueba que el candado está ocupado. */
    public boolean ocupado() {
        return candado.availablePermits() == 0;
    }
}
