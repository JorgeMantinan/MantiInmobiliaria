package es.manti.mercado.domain;

/**
 * Ya hay una importación de precios del MIVAU en curso: el endpoint admin es
 * único y los CSV pesan decenas de megas, así que no se admiten pasadas
 * superpuestas (la segunda se rechaza con 409 en vez de duplicar trabajo).
 */
public class ImportacionEnCursoException extends RuntimeException {

    public ImportacionEnCursoException() {
        super("Ya hay una importación de precios del MIVAU en curso; inténtelo de nuevo en unos segundos");
    }
}
