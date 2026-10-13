'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useSesion } from '@/features/auth/session';
import { actualizarAlerta, eliminarAlerta, listarAlertas } from '../api';
import { esSinSesion, mensajeDeError, MENSAJES } from '../mensajes';
import type { AlertaBusqueda } from '../types';

export type EstadoAlertas = Readonly<{
  /** Sin sesión (401 o `useSesion` sin usuario): no se llama a la API. */
  sinSesion: boolean;
  /** Primera carga en vuelo (o sesión aún restaurándose). */
  cargando: boolean;
  error: string | null;
  alertas: readonly AlertaBusqueda[];
  /** Error de la última acción (pausar/eliminar); no borra la lista. */
  errorAccion: string | null;
  /** Ids con un PATCH/DELETE en vuelo (para deshabilitar sus botones). */
  cambiando: ReadonlySet<number>;
}>;

export type AccionesAlertas = Readonly<{
  /** Activa/pausa la alerta (PATCH `activa`) y lo refleja en la lista local. */
  alternar: (alerta: AlertaBusqueda) => void;
  eliminar: (id: number) => void;
  reintentar: () => void;
}>;

/**
 * Listado de "Mis alertas" con acciones optimistas controladas:
 *
 * - `alternar` sólo pisa `activa` de la fila cuando el PATCH responde;
 * - `eliminar` sólo quita la fila cuando el DELETE responde;
 * - con sesión anónima no se lanza ninguna petición (la página muestra la CTA).
 */
export function useAlertas(): EstadoAlertas & AccionesAlertas {
  const { usuario, cargando: cargandoSesion } = useSesion();

  const [alertas, setAlertas] = useState<readonly AlertaBusqueda[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const [cambiando, setCambiando] = useState<ReadonlySet<number>>(() => new Set());
  const [sesionPerdida, setSesionPerdida] = useState(false);

  const cargadaInicial = useRef(false);

  const cargarPrimeraPagina = useCallback(async (): Promise<void> => {
    setCargando(true);
    setError(null);
    setErrorAccion(null);
    try {
      // Envelope AlertaPage (follow-up #98): el listado vive en `content`.
      const pagina = await listarAlertas();
      setAlertas(pagina.content);
    } catch (fallo: unknown) {
      if (esSinSesion(fallo)) {
        setSesionPerdida(true);
        return;
      }
      setError(mensajeDeError(fallo, MENSAJES.alertas));
    } finally {
      setCargando(false);
    }
  }, []);

  const sinSesion = sesionPerdida || (!cargandoSesion && usuario === null);

  // Primera carga: sólo cuando la sesión está resuelta y hay usuario.
  useEffect(() => {
    if (cargandoSesion || sinSesion || cargadaInicial.current) return;
    cargadaInicial.current = true;
    void cargarPrimeraPagina();
  }, [cargandoSesion, sinSesion, cargarPrimeraPagina]);

  const alternar = useCallback((alerta: AlertaBusqueda): void => {
    if (cambiando.has(alerta.id)) return;
    setErrorAccion(null);
    setCambiando((anterior) => new Set(anterior).add(alerta.id));
    void actualizarAlerta(alerta.id, { activa: !alerta.activa })
      .then((actualizada) => {
        setAlertas((anteriores) =>
          anteriores.map((fila) => (fila.id === actualizada.id ? actualizada : fila)),
        );
      })
      .catch((fallo: unknown) => {
        if (esSinSesion(fallo)) {
          setSesionPerdida(true);
          return;
        }
        setErrorAccion(mensajeDeError(fallo, MENSAJES.actualizar));
      })
      .finally(() => {
        setCambiando((anterior) => {
          const siguiente = new Set(anterior);
          siguiente.delete(alerta.id);
          return siguiente;
        });
      });
  }, [cambiando]);

  const eliminar = useCallback((id: number): void => {
    if (cambiando.has(id)) return;
    setErrorAccion(null);
    setCambiando((anterior) => new Set(anterior).add(id));
    void eliminarAlerta(id)
      .then(() => {
        // Baja confirmada: sólo entonces desaparece de la lista local.
        setAlertas((anteriores) => anteriores.filter((alerta) => alerta.id !== id));
      })
      .catch((fallo: unknown) => {
        if (esSinSesion(fallo)) {
          setSesionPerdida(true);
          return;
        }
        setErrorAccion(mensajeDeError(fallo, MENSAJES.eliminar));
      })
      .finally(() => {
        setCambiando((anterior) => {
          const siguiente = new Set(anterior);
          siguiente.delete(id);
          return siguiente;
        });
      });
  }, [cambiando]);

  const reintentar = useCallback((): void => {
    void cargarPrimeraPagina();
  }, [cargarPrimeraPagina]);

  return { sinSesion, cargando, error, alertas, errorAccion, cambiando, alternar, eliminar, reintentar };
}
