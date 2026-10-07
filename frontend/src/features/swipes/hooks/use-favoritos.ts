'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import type { InmuebleResumen } from '@/features/inmuebles/types';
import { useSesion } from '@/features/auth/session';
import { eliminarFavorito, listarFavoritos } from '../api';
import { esSinSesion, mensajeDeError, MENSAJES } from '../mensajes';

export type EstadoFavoritos = Readonly<{
  /** Sin sesión (401 o `useSesion` sin usuario): no se llama a la API. */
  sinSesion: boolean;
  /** Primera página todavía en vuelo (o sesión aún restaurándose). */
  cargando: boolean;
  error: string | null;
  favoritos: readonly InmuebleResumen[];
  hayMas: boolean;
  cargandoMas: boolean;
  /** Error del "Cargar más"; no borra lo ya cargado. */
  errorMas: string | null;
  /** Fallo del DELETE en curso (con su inmueble reinsertado). */
  errorQuitar: string | null;
  /** Ids cuyo DELETE sigue en vuelo (para deshabilitar su botón). */
  quitando: ReadonlySet<number>;
}>;

export type AccionesFavoritos = Readonly<{
  quitar: (inmuebleId: number) => void;
  cargarMas: () => void;
  reintentar: () => void;
}>;

/**
 * Listado de favoritos con paginación por cursor y baja confirmada:
 *
 * - `quitar` sólo borra de la lista LOCAL cuando el DELETE responde (si falla,
 *   se conserva el favorito y se anuncia el error; un 401 cae a la CTA).
 * - Con sesión anónima no se lanza ninguna petición.
 */
export function useFavoritos(): EstadoFavoritos & AccionesFavoritos {
  const { usuario, cargando: cargandoSesion } = useSesion();

  const [favoritos, setFavoritos] = useState<readonly InmuebleResumen[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  const [cargandoMas, setCargandoMas] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [errorMas, setErrorMas] = useState<string | null>(null);
  const [errorQuitar, setErrorQuitar] = useState<string | null>(null);
  const [quitando, setQuitando] = useState<ReadonlySet<number>>(() => new Set());
  const [sesionPerdida, setSesionPerdida] = useState(false);

  const cargadaInicial = useRef(false);
  // Ids con un DELETE en curso o ya confirmado: una página del cursor que
  // llegue después NO debe reinsertarlos en la lista.
  const quitadosRef = useRef<ReadonlySet<number>>(new Set());

  const anunciarFalloDeCarga = useCallback((fallo: unknown, porDefecto: string): void => {
    if (esSinSesion(fallo)) {
      setSesionPerdida(true);
      return;
    }
    setError(mensajeDeError(fallo, porDefecto));
  }, []);

  const cargarPrimeraPagina = useCallback(async (): Promise<void> => {
    setCargando(true);
    setError(null);
    setErrorMas(null);
    try {
      const pagina = await listarFavoritos();
      setFavoritos(pagina.content);
      setCursor(pagina.nextCursor);
    } catch (fallo: unknown) {
      anunciarFalloDeCarga(fallo, MENSAJES.favoritos);
    } finally {
      setCargando(false);
    }
  }, [anunciarFalloDeCarga]);

  const sinSesion = sesionPerdida || (!cargandoSesion && usuario === null);

  // Primera carga: sólo cuando la sesión está resuelta y hay usuario.
  useEffect(() => {
    if (cargandoSesion || sinSesion || cargadaInicial.current) return;
    cargadaInicial.current = true;
    void cargarPrimeraPagina();
  }, [cargandoSesion, sinSesion, cargarPrimeraPagina]);

  const quitar = useCallback((inmuebleId: number): void => {
    if (quitando.has(inmuebleId)) return;
    quitadosRef.current = new Set(quitadosRef.current).add(inmuebleId);
    setErrorQuitar(null);
    setQuitando((anterior) => new Set(anterior).add(inmuebleId));
    void eliminarFavorito(inmuebleId)
      .then(() => {
        // Baja confirmada: sólo entonces desaparece de la lista local.
        setFavoritos((anteriores) => anteriores.filter((favorito) => favorito.id !== inmuebleId));
      })
      .catch((fallo: unknown) => {
        if (esSinSesion(fallo)) {
          setSesionPerdida(true);
          return;
        }
        setErrorQuitar(mensajeDeError(fallo, MENSAJES.quitar));
      })
      .finally(() => {
        setQuitando((anterior) => {
          const siguiente = new Set(anterior);
          siguiente.delete(inmuebleId);
          return siguiente;
        });
      });
  }, [quitando]);

  const cargarMas = useCallback(async (): Promise<void> => {
    if (cursor === null || cargandoMas) return;
    setCargandoMas(true);
    setErrorMas(null);
    try {
      const pagina = await listarFavoritos(cursor);
      // Página vacía con cursor: corta el bucle de "Cargar más".
      if (pagina.content.length === 0) {
        setCursor(null);
        return;
      }
      setFavoritos((anteriores) => [
        ...anteriores,
        ...pagina.content.filter(
          (nuevo) =>
            !quitadosRef.current.has(nuevo.id) &&
            !anteriores.some((favorito) => favorito.id === nuevo.id),
        ),
      ]);
      setCursor(pagina.nextCursor);
    } catch (fallo: unknown) {
      if (esSinSesion(fallo)) {
        setSesionPerdida(true);
        return;
      }
      setErrorMas(mensajeDeError(fallo, MENSAJES.favoritos));
    } finally {
      setCargandoMas(false);
    }
  }, [cursor, cargandoMas]);

  const reintentar = useCallback((): void => {
    void cargarPrimeraPagina();
  }, [cargarPrimeraPagina]);

  return {
    sinSesion,
    cargando,
    error,
    favoritos,
    hayMas: cursor !== null,
    cargandoMas,
    errorMas,
    errorQuitar,
    quitando,
    quitar,
    cargarMas,
    reintentar,
  };
}
