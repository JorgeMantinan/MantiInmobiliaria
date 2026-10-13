import { etiquetaEstado, etiquetaGremio } from '../formato';

type BadgeEstadoProps = Readonly<{ estado: string }>;

/**
 * Variantes sólo con tokens de la paleta madera y contraste AA:
 * - `PENDIENTE`/`NUEVA`: texto nogal sobre crema (≈6:1).
 * - `ACTIVO`: crema sobre nogal oscuro (≈6:1).
 * - `RECHAZADO`/`CERRADA`: texto suave sobre superficie alta (≈5:1).
 */
const CLASES: Readonly<Record<string, string>> = {
  PENDIENTE: 'border-acento-nogal bg-superficie text-acento-hover',
  NUEVA: 'border-acento-nogal bg-superficie text-acento-hover',
  ACTIVO: 'border-acento-hover bg-acento-hover text-superficie',
  RECHAZADO: 'border-borde bg-superficie-alta text-texto-suave',
  CERRADA: 'border-borde bg-superficie-alta text-texto-suave',
};

const CLASE_POR_DEFECTO = 'border-borde bg-superficie-alta text-texto';

/** Badge de estado con la etiqueta legible (el estado SIEMPRE se lee en texto). */
export function BadgeEstado({ estado }: BadgeEstadoProps) {
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-md border px-2 py-0.5 text-xs font-medium ${CLASES[estado] ?? CLASE_POR_DEFECTO}`}
    >
      {etiquetaEstado(estado)}
    </span>
  );
}

/**
 * Chip "Destacado" de los inmuebles de portada. Nogal sobre crema para que
 * destaque sin salirse de la paleta madera (contraste AA).
 */
export function BadgeDestacado() {
  return (
    <span className="inline-flex shrink-0 items-center rounded-md border border-acento-nogal bg-superficie px-2 py-0.5 text-xs font-medium text-acento-hover">
      Destacado
    </span>
  );
}

/** Chips de gremios de una fila (slugs del DTO, ya legibles). */
export function ChipsGremios({ gremios }: Readonly<{ gremios: readonly string[] }>) {
  if (gremios.length === 0) {
    return <p className="text-sm text-texto-suave">Sin gremios indicados.</p>;
  }
  return (
    <ul className="flex list-none flex-wrap gap-2">
      {gremios.map((slug) => (
        <li
          key={slug}
          className="rounded-md border border-borde bg-superficie px-2.5 py-1 text-sm text-texto"
        >
          {etiquetaGremio(slug)}
        </li>
      ))}
    </ul>
  );
}
