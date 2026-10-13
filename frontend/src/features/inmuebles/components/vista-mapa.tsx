'use client';

import type { InmuebleResumen } from '../types';
import { MapaListadoWrapper } from './mapa-listado-wrapper';

type VistaMapaProps = Readonly<{
  /** Mismos inmuebles del hook (página actual + acumulados de "Cargar más"). */
  inmuebles: readonly InmuebleResumen[];
}>;

/**
 * Vista Mapa del listado: un `role="status"` (aria-live) anuncia cuántos
 * inmuebles hay en el mapa —los marcadores son un complemento del listado en
 * texto de la otra vista— y el mapa deriva de los datos ya cargados.
 */
export function VistaMapa({ inmuebles }: VistaMapaProps) {
  return (
    <div>
      <p role="status" className="text-sm text-texto-suave">
        {inmuebles.length === 1
          ? '1 inmueble en el mapa'
          : `${inmuebles.length} inmuebles en el mapa`}
      </p>
      <div className="mt-4">
        <MapaListadoWrapper inmuebles={inmuebles} />
      </div>
    </div>
  );
}
