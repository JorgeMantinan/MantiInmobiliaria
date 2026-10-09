'use client';

import { useCallback, useEffect, useState } from 'react';
import { cambiarEstadoProfesional, listarProfesionales } from '../api';
import { etiquetaEstado } from '../formato';
import { MENSAJES, mensajeDeError } from '../mensajes';
import type { EstadoModeracion, EstadoProfesional, ProfesionalAdmin } from '../types';

/** Resultado de UNA carga: sólo se pinta si coincide con la pestaña actual. */
type ResultadoCarga = Readonly<{
  estado: EstadoProfesional;
  /** Valor del contador `recarga` con el que se lanzó la carga. */
  recarga: number;
  filas: readonly ProfesionalAdmin[];
  error: string | null;
}>;

/** Aviso de acción asociado a la pestaña en la que se produjo. */
type AvisoAccion = Readonly<{ estado: EstadoProfesional; texto: string }>;

/** Estado de la lista de moderación (carga, errores y avisos de acción). */
export type EstadoPanelProfesionales = Readonly<{
  cargando: boolean;
  profesionales: readonly ProfesionalAdmin[];
  /** Error de la carga de la pestaña (`role="alert"` + reintento). */
  error: string | null;
  /** Éxito de la última moderación (`aria-live="polite"`). */
  aviso: string | null;
  /** Fallo del último PATCH, tras deshacer el cambio optimista (`role="alert"`). */
  errorAccion: string | null;
  /** ids con un PATCH en vuelo: sus botones quedan deshabilitados. */
  enVuelo: readonly number[];
}>;

export type AccionesModeracion = Readonly<{
  /** Cambio optimista de estado con rollback y avisos si el PATCH falla. */
  moderar: (profesional: ProfesionalAdmin, destino: EstadoModeracion) => Promise<void>;
  reintentar: () => void;
}>;

/**
 * Listado de profesionales de una pestaña + moderación optimista.
 *
 * - Carga por `estado` (cambia al cambiar de pestaña). El resultado sólo se
 *   pinta si coincide con la pestaña y el contador de recarga ACTUALES, así
 *   que las respuestas obsoletas se descartan sin estado extra.
 * - `moderar` pinta el nuevo estado ANTES de esperar al servidor; si el
 *   PATCH falla, la fila vuelve a su estado anterior y el error queda
 *   visible en `errorAccion`.
 */
export function useProfesionalesAdmin(
  estado: EstadoProfesional,
): EstadoPanelProfesionales & AccionesModeracion {
  /** `null` = todavía no hay resultado para ESTA pestaña (⇒ cargando). */
  const [resultado, setResultado] = useState<ResultadoCarga | null>(null);
  const [aviso, setAviso] = useState<AvisoAccion | null>(null);
  const [errorAccion, setErrorAccion] = useState<AvisoAccion | null>(null);
  const [enVuelo, setEnVuelo] = useState<readonly number[]>([]);
  const [recarga, setRecarga] = useState(0);

  useEffect(() => {
    let vigente = true;
    listarProfesionales(estado)
      .then((filas) => {
        if (vigente) setResultado({ estado, recarga, filas, error: null });
      })
      .catch((causa: unknown) => {
        if (!vigente) return;
        setResultado({
          estado,
          recarga,
          filas: [],
          error: mensajeDeError(causa, MENSAJES.profesionales),
        });
      });
    return () => {
      vigente = false;
    };
  }, [estado, recarga]);

  const moderar = useCallback(
    async (profesional: ProfesionalAdmin, destino: EstadoModeracion): Promise<void> => {
      const estadoAnterior = profesional.estado;
      const pestanaActual = estado;
      /** El resultado en pantalla es ESTE (pestaña y recarga actuales). */
      const esEsteResultado = (prev: ResultadoCarga | null): prev is ResultadoCarga =>
        prev !== null && prev.estado === pestanaActual && prev.recarga === recarga;
      setAviso(null);
      setErrorAccion(null);
      setEnVuelo((actual) => [...actual, profesional.id]);
      // Optimista: la fila cambia de badge sin esperar al servidor.
      setResultado((prev) => (esEsteResultado(prev) ? cambiarFila(prev, profesional.id, destino) : prev));
      try {
        await cambiarEstadoProfesional(profesional.id, destino);
        setAviso({
          estado: pestanaActual,
          texto: `${profesional.nombre} ahora está en estado ${etiquetaEstado(destino)}.`,
        });
      } catch (causa: unknown) {
        // Rollback: la fila recupera su estado anterior y el fallo se ve.
        setResultado((prev) =>
          esEsteResultado(prev) ? cambiarFila(prev, profesional.id, estadoAnterior) : prev,
        );
        setErrorAccion({
          estado: pestanaActual,
          texto: mensajeDeError(causa, MENSAJES.cambioEstado),
        });
      } finally {
        setEnVuelo((actual) => actual.filter((id) => id !== profesional.id));
      }
    },
    [estado, recarga],
  );

  const reintentar = useCallback(() => setRecarga((anterior) => anterior + 1), []);

  const vigente = resultado !== null && resultado.estado === estado && resultado.recarga === recarga;

  return {
    cargando: !vigente,
    profesionales: vigente ? resultado.filas : [],
    error: vigente ? resultado.error : null,
    aviso: aviso?.estado === estado ? aviso.texto : null,
    errorAccion: errorAccion?.estado === estado ? errorAccion.texto : null,
    enVuelo,
    moderar,
    reintentar,
  };
}

/** Sustituye una fila por su versión con `estado` cambiado (optimismo/rollback). */
function cambiarFila(
  resultado: ResultadoCarga,
  id: number,
  estado: EstadoProfesional,
): ResultadoCarga {
  return {
    ...resultado,
    filas: resultado.filas.map((fila) => (fila.id === id ? { ...fila, estado } : fila)),
  };
}
