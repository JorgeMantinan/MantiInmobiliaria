import { PRECIO_MAXIMO_CONSULTA } from '../filtros';
import { CampoFiltro, CLASE_CAMPO } from './campo-filtro';

/** Id del aviso de rango, para asociarlo a los inputs con `aria-describedby`. */
export const AVISO_RANGO_ID = 'aviso-rango-precio';

type CamposPrecioProps = Readonly<{
  /** Valores con los que arrancan los inputs (los de la URL). */
  precioMin?: string;
  precioMax?: string;
  /** Mensaje de rango invertido; `null` sin aviso. */
  avisoRango: string | null;
}>;

/**
 * Par `precioMin`/`precioMax` con su aviso de rango invertido. Los dos inputs
 * quedan asociados al aviso (`aria-describedby`) y marcados `aria-invalid`
 * cuando el rango no se puede enviar al backend.
 */
export function CamposPrecio({ precioMin, precioMax, avisoRango }: CamposPrecioProps) {
  const conAviso = avisoRango !== null;
  const descritoPor = conAviso ? AVISO_RANGO_ID : undefined;

  return (
    <>
      <CampoFiltro id="filtro-precio-min" etiqueta="Precio mínimo (€)">
        <input
          id="filtro-precio-min"
          name="precioMin"
          type="number"
          min={0}
          max={PRECIO_MAXIMO_CONSULTA}
          inputMode="numeric"
          defaultValue={precioMin ?? ''}
          aria-describedby={descritoPor}
          aria-invalid={conAviso || undefined}
          className={CLASE_CAMPO}
        />
      </CampoFiltro>

      <CampoFiltro id="filtro-precio-max" etiqueta="Precio máximo (€)">
        <input
          id="filtro-precio-max"
          name="precioMax"
          type="number"
          min={0}
          max={PRECIO_MAXIMO_CONSULTA}
          inputMode="numeric"
          defaultValue={precioMax ?? ''}
          aria-describedby={descritoPor}
          aria-invalid={conAviso || undefined}
          className={CLASE_CAMPO}
        />
      </CampoFiltro>

      {conAviso ? (
        <p id={AVISO_RANGO_ID} role="status" className="col-span-full text-sm text-acento-hover">
          {avisoRango}
        </p>
      ) : null}
    </>
  );
}
