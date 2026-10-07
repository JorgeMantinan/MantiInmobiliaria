'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { listarInmuebles } from '../api';
import { construirQueryInmuebles, leerFiltros } from '../filtros';
import type { FiltrosInmuebles, InmuebleResumen } from '../types';

/** Resultado de una primera-carga: se descarta si ya no son esos filtros. */
type ResultadoBusqueda = Readonly<{
  filtros: FiltrosInmuebles;
  inmuebles: readonly InmuebleResumen[];
  cursor: string | null;
  total: number;
  /** Error sin resultados que mostrar (primera carga). */
  error: string | null;
  /** Error del "Cargar más"; no borra lo ya cargado. */
  errorMas: string | null;
}>;

/** Estado de la lista: mientras `cargando` no haya resultado de ESTOS filtros. */
export type ListadoEstado = Readonly<{
  filtros: FiltrosInmuebles;
  /** Query exacta de la URL; sirve de `key` para reiniciar el formulario. */
  consulta: string;
  cargando: boolean;
  inmuebles: readonly InmuebleResumen[];
  total: number;
  hayMas: boolean;
  cargandoMas: boolean;
  error: string | null;
  errorMas: string | null;
}>;

/** Acciones de la lista; `aplicar` sincroniza los filtros en la URL. */
export type ListadoAcciones = Readonly<{
  aplicar: (filtros: FiltrosInmuebles) => void;
  limpiar: () => void;
  reintentar: () => void;
  cargarMas: () => void;
}>;

function mensajeDeError(error: unknown): string {
  return error instanceof Error ? error.message : 'No se han podido cargar los inmuebles.';
}

function resultadoConPagina(
  filtros: FiltrosInmuebles,
  inmuebles: readonly InmuebleResumen[],
  cursor: string | null,
  total: number,
): ResultadoBusqueda {
  return { filtros, inmuebles, cursor, total, error: null, errorMas: null };
}

function resultadoConError(filtros: FiltrosInmuebles, error: unknown): ResultadoBusqueda {
  return {
    filtros,
    inmuebles: [],
    cursor: null,
    total: 0,
    error: mensajeDeError(error),
    errorMas: null,
  };
}

/**
 * Trae la primera página SIN tocar estado (para poder usarla tanto desde el
 * `useEffect` como desde el reintento del botón "Reintentar").
 */
async function traerPrimeraPagina(filtros: FiltrosInmuebles): Promise<ResultadoBusqueda> {
  try {
    const pagina = await listarInmuebles(filtros);
    return resultadoConPagina(filtros, pagina.content, pagina.nextCursor, pagina.totalElements);
  } catch (errorDeCarga: unknown) {
    return resultadoConError(filtros, errorDeCarga);
  }
}

function idsDe(resultado: ResultadoBusqueda): ReadonlySet<number> {
  return new Set(resultado.inmuebles.map((inmueble) => inmueble.id));
}

/**
 * Listado público: filtros sincronizados con la URL (`router.replace` +
 * `searchParams`), paginación por cursor que va acumulando y estados de
 * carga / error / vacío. Toda la lógica de datos vive aquí; la vista sólo
 * pinta y llama a las acciones.
 */
export function useListadoInmuebles(): ListadoEstado & ListadoAcciones {
  const router = useRouter();
  const searchParams = useSearchParams();
  const consulta = searchParams.toString();
  const filtros = useMemo(() => leerFiltros(new URLSearchParams(consulta)), [consulta]);

  const [resultado, setResultado] = useState<ResultadoBusqueda | null>(null);
  const [cargandoMas, setCargandoMas] = useState(false);
  const idsCargados = useRef<ReadonlySet<number>>(new Set());

  useEffect(() => {
    let cancelado = false;
    traerPrimeraPagina(filtros).then((resultadoNuevo) => {
      if (cancelado) return;
      idsCargados.current = idsDe(resultadoNuevo);
      setResultado(resultadoNuevo);
    });
    return () => {
      cancelado = true;
    };
  }, [filtros]);

  const paginaActual = resultado !== null && resultado.filtros === filtros ? resultado : null;

  const aplicar = useCallback(
    (nuevos: FiltrosInmuebles) => {
      router.replace(`/inmuebles${construirQueryInmuebles(nuevos)}`, { scroll: false });
    },
    [router],
  );

  const limpiar = useCallback(() => {
    router.replace('/inmuebles', { scroll: false });
  }, [router]);

  const reintentar = useCallback(() => {
    setResultado(null);
    void traerPrimeraPagina(filtros).then((resultadoNuevo) => {
      idsCargados.current = idsDe(resultadoNuevo);
      setResultado(resultadoNuevo);
    });
  }, [filtros]);

  const cargarMas = useCallback(async () => {
    if (paginaActual === null || paginaActual.cursor === null || cargandoMas) return;
    const anteriores = paginaActual;
    setCargandoMas(true);
    try {
      const siguiente = await listarInmuebles(filtros, { cursor: anteriores.cursor });
      const nuevos = siguiente.content.filter((i) => !idsCargados.current.has(i.id));
      const acumulados = new Set(idsCargados.current);
      for (const inmueble of nuevos) {
        acumulados.add(inmueble.id);
      }
      idsCargados.current = acumulados;
      setResultado({
        ...anteriores,
        inmuebles: [...anteriores.inmuebles, ...nuevos],
        cursor: siguiente.nextCursor,
        errorMas: null,
      });
    } catch (errorDeCarga: unknown) {
      setResultado({ ...anteriores, errorMas: mensajeDeError(errorDeCarga) });
    } finally {
      setCargandoMas(false);
    }
  }, [paginaActual, cargandoMas, filtros]);

  return {
    filtros,
    consulta,
    cargando: paginaActual === null,
    inmuebles: paginaActual?.inmuebles ?? [],
    total: paginaActual?.total ?? 0,
    hayMas: (paginaActual?.cursor ?? null) !== null,
    cargandoMas,
    error: paginaActual?.error ?? null,
    errorMas: paginaActual?.errorMas ?? null,
    aplicar,
    limpiar,
    reintentar,
    cargarMas,
  };
}
