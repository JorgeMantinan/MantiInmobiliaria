'use client';

import type { VistaListado } from '../types';

type ConmutadorVistaProps = Readonly<{
  vista: VistaListado;
  onCambiar: (vista: VistaListado) => void;
}>;

type OpcionVista = Readonly<{ valor: VistaListado; etiqueta: string }>;

const OPCIONES: readonly OpcionVista[] = [
  { valor: 'lista', etiqueta: 'Lista' },
  { valor: 'mapa', etiqueta: 'Mapa' },
];

/** Estilo madera del conmutador: la opción activa usa el botón primario. */
function claseDe(activa: boolean): string {
  const base =
    'rounded-md px-4 py-2 text-sm font-medium transition-colors outline-none focus:ring-2 focus:ring-acento-nogal';
  if (activa) return `${base} bg-acento-nogal text-superficie`;
  return `${base} border border-borde bg-transparent text-texto hover:bg-superficie-alta`;
}

/**
 * Conmutador Lista | Mapa del listado. Es un grupo de botones reales con
 * `aria-pressed`: la vista activa se comunica por estado accesible, no sólo
 * por color, y el cambio se escribe en la URL desde el contenedor.
 */
export function ConmutadorVista({ vista, onCambiar }: ConmutadorVistaProps) {
  return (
    <div role="group" aria-label="Forma de ver los resultados" className="flex gap-2">
      {OPCIONES.map((opcion) => (
        <button
          key={opcion.valor}
          type="button"
          aria-pressed={vista === opcion.valor}
          className={claseDe(vista === opcion.valor)}
          onClick={() => onCambiar(opcion.valor)}
        >
          {opcion.etiqueta}
        </button>
      ))}
    </div>
  );
}
