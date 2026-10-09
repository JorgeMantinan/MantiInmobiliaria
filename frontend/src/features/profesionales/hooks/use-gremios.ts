'use client';

import { useEffect, useState } from 'react';
import { listarGremios } from '../api';
import { MENSAJES } from '../mensajes';
import { GREMIOS_DE_RESERVA } from '../types';
import type { Gremio } from '../types';

export type EstadoGremios = Readonly<{
  /** Catálogo del servidor; lista de reserva si la API falla o devuelve vacío. */
  gremios: readonly Gremio[];
  cargando: boolean;
  /** Aviso cuando se usa la lista de reserva; `null` en el camino feliz. */
  aviso: string | null;
}>;

const CARGANDO: EstadoGremios = { gremios: [], cargando: true, aviso: null };

/**
 * Carga `GET /api/v1/gremios` con estados de carga/error. Si la API falla el
 * formulario NO se bloquea: se usa `GREMIOS_DE_RESERVA` y se deja un aviso.
 */
export function useGremios(): EstadoGremios {
  const [estado, setEstado] = useState<EstadoGremios>(CARGANDO);

  useEffect(() => {
    let vigente = true;
    listarGremios()
      .then((gremios) => {
        if (!vigente) return;
        setEstado(
          gremios.length === 0
            ? { gremios: GREMIOS_DE_RESERVA, cargando: false, aviso: MENSAJES.gremiosReserva }
            : { gremios, cargando: false, aviso: null },
        );
      })
      .catch(() => {
        if (!vigente) return;
        setEstado({
          gremios: GREMIOS_DE_RESERVA,
          cargando: false,
          aviso: MENSAJES.gremiosReserva,
        });
      });
    return () => {
      vigente = false;
    };
  }, []);

  return estado;
}
