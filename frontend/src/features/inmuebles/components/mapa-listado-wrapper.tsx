'use client';

import dynamic from 'next/dynamic';
import type { InmuebleResumen } from '../types';

/**
 * Leaflet sólo puede ejecutarse en el navegador, así que el mapa del listado
 * se carga con `ssr: false` desde un client component (mismo patrón que
 * `mapa-detalle-wrapper`). El placeholder ocupa EXACTAMENTE la misma altura
 * (`h-[600px]`) que el mapa para que no haya salto de layout al montarse.
 */
const MapaListado = dynamic(() => import('./mapa-listado').then((modulo) => modulo.MapaListado), {
  ssr: false,
  loading: () => (
    <div
      aria-hidden="true"
      className="h-[600px] w-full animate-pulse rounded-md border border-borde bg-superficie-alta"
    />
  ),
});

type MapaListadoWrapperProps = Readonly<{
  /** Inmuebles YA cargados por el hook del listado; el mapa no hace fetch. */
  inmuebles: readonly InmuebleResumen[];
}>;

/** Punto de entrada seguro para la vista Mapa del listado público. */
export function MapaListadoWrapper(props: MapaListadoWrapperProps) {
  return <MapaListado {...props} />;
}
