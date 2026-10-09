'use client';

import { useCallback, useEffect, useState } from 'react';
import { listarSolicitudes } from '../api';
import { MENSAJES, mensajeDeError } from '../mensajes';
import type { SolicitudAdmin } from '../types';

/** Resultado de UNA carga: sólo se pinta si coincide con el contador actual. */
type ResultadoCarga = Readonly<{
  /** Valor del contador `recarga` con el que se lanzó la carga. */
  recarga: number;
  filas: readonly SolicitudAdmin[];
  error: string | null;
}>;

/** Estado del listado de solicitudes (sólo lectura). */
export type EstadoSolicitudes = Readonly<{
  cargando: boolean;
  solicitudes: readonly SolicitudAdmin[];
  error: string | null;
}>;

/**
 * Últimas 50 solicitudes. Sólo lectura: el backend todavía no expone el
 * cierre de una solicitud (TODO de issue aparte).
 */
export function useSolicitudesAdmin(): EstadoSolicitudes & { reintentar: () => void } {
  /** `null` = todavía no hay resultado (⇒ cargando). */
  const [resultado, setResultado] = useState<ResultadoCarga | null>(null);
  const [recarga, setRecarga] = useState(0);

  useEffect(() => {
    let vigente = true;
    listarSolicitudes()
      .then((filas) => {
        if (vigente) setResultado({ recarga, filas, error: null });
      })
      .catch((causa: unknown) => {
        if (!vigente) return;
        setResultado({ recarga, filas: [], error: mensajeDeError(causa, MENSAJES.solicitudes) });
      });
    return () => {
      vigente = false;
    };
  }, [recarga]);

  const reintentar = useCallback(() => setRecarga((anterior) => anterior + 1), []);

  const vigente = resultado !== null && resultado.recarga === recarga;

  return {
    cargando: !vigente,
    solicitudes: vigente ? resultado.filas : [],
    error: vigente ? resultado.error : null,
    reintentar,
  };
}
