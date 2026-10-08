import { AVISO_ERROR } from '@/components/ui/avisos';
import type { EstadoGremios } from '../hooks/use-gremios';
import { GREMIOS_MAXIMOS, GREMIOS_MINIMOS } from '../validacion';

type SelectorGremiosProps = Readonly<{
  /** Prefijo de los `id`/referencias accesibles generados (p. ej. `solicitud-gremio`). */
  id: string;
  /** Resultado de `useGremios`: catálogo, carga y aviso de reserva. */
  estado: EstadoGremios;
  /** Slugs marcados (controlado desde el formulario). */
  seleccion: readonly string[];
  onCambio: (slugs: readonly string[]) => void;
  /** Error de validación; si existe sustituye al contador (rol `alert`). */
  error?: string;
}>;

/** Estado del badge: la inicial del gremio en serif, sin emojis. */
const CLASE_BADGE =
  'flex size-7 shrink-0 items-center justify-center rounded-md border border-borde bg-superficie font-display text-sm text-acento-nogal';

function inicialDe(nombre: string): string {
  return nombre.trim().charAt(0).toLocaleUpperCase('es');
}

function avisoDe(seleccion: readonly string[]): string {
  if (seleccion.length === 0) {
    return `Selecciona al menos ${GREMIOS_MINIMOS} gremio.`;
  }
  if (seleccion.length >= GREMIOS_MAXIMOS) {
    return `Máximo ${GREMIOS_MAXIMOS} gremios: deselecciona alguno para cambiar la selección.`;
  }
  return `${seleccion.length} de ${GREMIOS_MAXIMOS} gremios seleccionados.`;
}

/**
 * Selección múltiple de gremios con checkbox REAL (`sr-only`) dentro de cada
 * etiqueta clicable, badge tipográfico con la inicial y límite de
 * {@link GREMIOS_MAXIMOS}. Si la carga sigue en curso pinta su esqueleto.
 */
export function SelectorGremios({
  id,
  estado,
  seleccion,
  onCambio,
  error,
}: SelectorGremiosProps) {
  if (estado.cargando) {
    return <SelectorGremiosCargando />;
  }

  const completo = seleccion.length >= GREMIOS_MAXIMOS;

  function alternar(slug: string, marcado: boolean): void {
    if (marcado) {
      if (seleccion.includes(slug) || seleccion.length >= GREMIOS_MAXIMOS) return;
      onCambio([...seleccion, slug]);
      return;
    }
    onCambio(seleccion.filter((actual) => actual !== slug));
  }

  const idAviso = error === undefined ? `${id}-aviso` : `${id}-error`;

  return (
    <fieldset className="m-0 min-w-0 border-0 p-0" aria-describedby={idAviso}>
      <legend className="mb-2 p-0 text-sm font-medium text-texto">
        Gremios{' '}
        <span className="font-normal text-texto-suave">
          (elige entre {GREMIOS_MINIMOS} y {GREMIOS_MAXIMOS})
        </span>
      </legend>
      <ul className="grid list-none gap-2 sm:grid-cols-2">
        {estado.gremios.map((gremio) => {
          const marcado = seleccion.includes(gremio.slug);
          const bloqueado = !marcado && completo;
          const idCampo = `${id}-${gremio.slug}`;
          return (
            <li key={gremio.slug} className="relative">
              <input
                type="checkbox"
                id={idCampo}
                checked={marcado}
                disabled={bloqueado}
                onChange={(evento) => alternar(gremio.slug, evento.target.checked)}
                className="peer sr-only"
              />
              <label
                htmlFor={idCampo}
                className={`flex cursor-pointer items-center gap-3 rounded-md border px-3 py-2.5 text-sm transition-colors outline-none peer-focus-visible:ring-2 peer-focus-visible:ring-acento-nogal peer-disabled:cursor-not-allowed peer-disabled:opacity-60 ${
                  marcado
                    ? 'border-acento-nogal bg-superficie-alta text-texto'
                    : 'border-borde bg-superficie text-texto hover:bg-superficie-alta'
                }`}
              >
                <span aria-hidden="true" className={CLASE_BADGE}>
                  {inicialDe(gremio.nombre)}
                </span>
                <span>{gremio.nombre}</span>
                {marcado ? (
                  <svg
                    aria-hidden="true"
                    viewBox="0 0 20 20"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.5"
                    className="ml-auto size-4 shrink-0 text-acento-nogal"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" d="M4 10.5 8 14.5 16 6" />
                  </svg>
                ) : null}
              </label>
            </li>
          );
        })}
      </ul>
      {error === undefined ? (
        <p id={`${id}-aviso`} role="status" className="mt-2 text-xs text-texto-suave">
          {avisoDe(seleccion)}
        </p>
      ) : (
        <p id={`${id}-error`} role="alert" className={AVISO_ERROR}>
          {error}
        </p>
      )}
      {estado.aviso !== null ? (
        <p role="alert" className={`mt-2 ${AVISO_ERROR}`}>
          {estado.aviso}
        </p>
      ) : null}
    </fieldset>
  );
}

/** Esqueleto del selector mientras llega `GET /api/v1/gremios`. */
export function SelectorGremiosCargando() {
  return (
    <div role="status">
      <span className="sr-only">Cargando gremios…</span>
      <ul aria-hidden="true" className="grid list-none gap-2 sm:grid-cols-2">
        {Array.from({ length: 6 }, (_, indice) => (
          <li
            key={indice}
            className="h-11 animate-pulse rounded-md border border-borde bg-superficie-alta"
          />
        ))}
      </ul>
    </div>
  );
}
