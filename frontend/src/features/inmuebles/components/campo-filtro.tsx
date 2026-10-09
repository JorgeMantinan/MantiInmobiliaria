import type { ReactNode } from 'react';

/** Clase compartida de los campos del formulario de filtros (input/select). */
export const CLASE_CAMPO =
  'rounded-md border border-borde bg-superficie px-3 py-2 text-sm text-texto outline-none transition-colors focus:ring-2 focus:ring-acento-nogal';

export const CLASE_ETIQUETA = 'block text-sm font-medium text-texto';

type CampoFiltroProps = Readonly<{
  id: string;
  etiqueta: string;
  children: ReactNode;
}>;

/** Contenedor de un campo con su label accesible (los inputs son no controlados). */
export function CampoFiltro({ id, etiqueta, children }: CampoFiltroProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className={CLASE_ETIQUETA}>
        {etiqueta}
      </label>
      {children}
    </div>
  );
}
