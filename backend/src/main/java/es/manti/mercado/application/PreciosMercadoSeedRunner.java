package es.manti.mercado.application;

import es.manti.mercado.domain.PrecioMercadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Sembrado local de precios del MIVAU en el perfil {@code dev}.
 *
 * <p>Sólo si la tabla está vacía y SÓLO en dev: en test no existe esta clase
 * (la suite jamás toca la red) y en prod el responsable de refrescar los datos
 * es un ADMIN vía {@code POST /api/v1/admin/mercado/actualizar}.</p>
 *
 * <p>El CDN del MIVAU puede estar caído: un fallo se registra y NO impide
 * arrancar — la ficha simplemente no mostrará precios hasta el siguiente
 * reinicio con la tabla vacía o hasta que un ADMIN lo reintente.</p>
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class PreciosMercadoSeedRunner implements ApplicationRunner {

    private final PreciosMercadoImportador importador;
    private final PrecioMercadoRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        if (repository.contar() > 0) {
            return;
        }
        try {
            importador.importar();
        } catch (RuntimeException e) {
            log.warn("No se han podido importar los precios del MIVAU en dev: {}. "
                    + "Reinténtalo desde el backoffice (POST /api/v1/admin/mercado/actualizar).",
                    e.getMessage());
        }
    }
}
