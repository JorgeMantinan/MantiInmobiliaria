'use client';

import dynamic from 'next/dynamic';

/**
 * Leaflet sólo puede ejecutarse en el navegador, así que el mapa se carga con
 * `ssr: false` desde un client component (en server components no está
 * permitido). El placeholder ocupa EXACTAMENTE la misma altura (`h-72`) que el
 * mapa para que no haya salto de layout al montarse.
 */
const MapaDetalle = dynamic(() => import('./mapa-detalle').then((modulo) => modulo.MapaDetalle), {
  ssr: false,
  loading: () => (
    <div
      aria-hidden="true"
      className="h-72 w-full animate-pulse rounded-md border border-borde bg-superficie-alta"
    />
  ),
});

type MapaDetalleWrapperProps = Readonly<{
  lat: number;
  lng: number;
  titulo: string;
}>;

/** Punto de entrada seguro para páginas y componentes de la ficha. */
export function MapaDetalleWrapper(props: MapaDetalleWrapperProps) {
  return <MapaDetalle {...props} />;
}
