'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { listarInmuebles } from '@/features/inmuebles/api';
import { construirQueryInmuebles, leerFiltros } from '@/features/inmuebles/filtros';
import type { FiltrosInmuebles, InmuebleResumen } from '@/features/inmuebles/types';
import { eliminarInmueble } from '../api';
import { MENSAJES, mensajeDeError } from '../mensajes';

/** Filtros del backoffice: subconjunto enlazable del endpoint público. */
export type FiltrosAdmin = Readonly<{
  estado?: 'VENTA' | 'ALQUILER';
  municipio?: string;
  destacado?: boolean;
}>;

/** Página vigente (filtros + recarga actuales) o `null` mientras carga. */
type PaginaVigente = Readonly<{
  filtros: FiltrosInmuebles;
  recarga: number;
  filas: readonly InmuebleResumen[];
  cursor: string | null;
  /** Total de la primera página; `null` en las siguientes (sin COUNT por salto). */
  total: number | null;
  error: string | null;
  errorMas: string | null;
}>;

/** Aviso de borrado ligado a la página (filtros+recarga) en la que ocurrió. */
type AvisoBorrado = Readonly<{ filtros: FiltrosInmuebles; recarga: number; texto: string }>;

export type EstadoPanelInmuebles = Readonly<{
  cargando: boolean;
  inmuebles: readonly InmuebleResumen[];
  total: number | null;
  hayMas: boolean;
  cargandoMas: boolean;
  error: string | null;
  errorMas: string | null;
  /** Éxito del último borrado (`aria-live="polite"`). */
  aviso: string | null;
  /** Fallo del último DELETE, tras deshacer el borrado optimista. */
  errorAccion: string | null;
  /** ids con un DELETE en vuelo (fila con botones deshabilitados). */
  enVuelo: readonly number[];
}>;

export type AccionesAdminInmuebles = Readonly<{
  aplicar: (filtros: FiltrosAdmin) => void;
  limpiar: () => void;
  reintentar: () => void;
  cargarMas: () => void;
  /** Borrado optimista: la fila sale ya; si el DELETE falla, vuelve. */
  eliminar: (inmueble: InmuebleResumen) => Promise<void>;
}>;

async function traerPrimeraPagina(
  filtros: FiltrosInmuebles,
  recarga: number,
): Promise<PaginaVigente> {
  try {
    const pagina = await listarInmuebles(filtros);
    return {
      filtros,
      recarga,
      filas: pagina.content,
      cursor: pagina.nextCursor,
      total: pagina.totalElements,
      error: null,
      errorMas: null,
    };
  } catch (causa: unknown) {
    return {
      filtros,
      recarga,
      filas: [],
      cursor: null,
      total: null,
      error: mensajeDeError(causa, MENSAJES.inmuebles),
      errorMas: null,
    };
  }
}

/**
 * Listado del backoffice sobre el endpoint público `GET /api/v1/inmuebles`
 * (filtros enlazables en la URL), paginación por cursor y borrado optimista
 * con rollback (patrón de la moderación de profesionales).
 */
export function useInmueblesAdmin(): EstadoPanelInmuebles & AccionesAdminInmuebles {
  const router = useRouter();
  const searchParams = useSearchParams();
  const consulta = searchParams.toString();
  const filtros = useMemo(() => leerFiltros(new URLSearchParams(consulta)), [consulta]);

  /** `null` = todavía no hay página para ESTOS filtros (⇒ cargando). */
  const [pagina, setPagina] = useState<PaginaVigente | null>(null);
  const [aviso, setAviso] = useState<AvisoBorrado | null>(null);
  const [errorAccion, setErrorAccion] = useState<AvisoBorrado | null>(null);
  const [enVuelo, setEnVuelo] = useState<readonly number[]>([]);
  const [cargandoMas, setCargandoMas] = useState(false);
  const [recarga, setRecarga] = useState(0);
  /**
   * Contador de generaciones de "cargar más": una página siguiente lanzada
   * con filtros o recarga antiguos se descarta al llegar.
   */
  const generacion = useRef(0);
  /** ids ya pintados: el cursor es estable, pero un borrado desplaza páginas. */
  const idsCargados = useRef<ReadonlySet<number>>(new Set());

  useEffect(() => {
    let vigente = true;
    // Invalida cualquier "cargar más" en vuelo con la generación ANTERIOR:
    // su respuesta se descartará al llegar (mismo patrón que
    // `use-listado-inmuebles`, líneas 96-106). Sin esto, una página siguiente
    // vieja pisaría esta página y dejaría `pagina.filtros` ≠ filtros actuales
    // (esqueleto permanente).
    ++generacion.current;
    idsCargados.current = new Set();
    traerPrimeraPagina(filtros, recarga).then((nueva) => {
      if (!vigente) return;
      idsCargados.current = new Set(nueva.filas.map((fila) => fila.id));
      setCargandoMas(false);
      setPagina(nueva);
    });
    return () => {
      vigente = false;
    };
  }, [filtros, recarga]);

  const vigente = pagina !== null && pagina.filtros === filtros && pagina.recarga === recarga;

  const aplicar = useCallback(
    (nuevos: FiltrosAdmin) => {
      const filtrosNuevos: FiltrosInmuebles = {
        ...(nuevos.estado !== undefined ? { estado: nuevos.estado } : {}),
        ...(nuevos.municipio !== undefined ? { municipio: nuevos.municipio } : {}),
        ...(nuevos.destacado !== undefined ? { destacado: nuevos.destacado } : {}),
      };
      router.replace(`/admin/inmuebles${construirQueryInmuebles(filtrosNuevos)}`, {
        scroll: false,
      });
    },
    [router],
  );

  const limpiar = useCallback(() => {
    router.replace('/admin/inmuebles', { scroll: false });
  }, [router]);

  const reintentar = useCallback(() => setRecarga((anterior) => anterior + 1), []);

  const cargarMas = useCallback(async () => {
    if (!vigente || pagina.cursor === null || cargandoMas) return;
    const anteriores = pagina;
    const filtrosAlLanzar = filtros;
    const generacionAlLanzar = ++generacion.current;
    const sigueVigente = () => generacionAlLanzar === generacion.current;

    setCargandoMas(true);
    try {
      const siguiente = await listarInmuebles(filtrosAlLanzar, { cursor: anteriores.cursor });
      if (!sigueVigente()) return;
      const nuevos = siguiente.content.filter((i) => !idsCargados.current.has(i.id));
      const acumulados = new Set(idsCargados.current);
      for (const inmueble of nuevos) {
        acumulados.add(inmueble.id);
      }
      idsCargados.current = acumulados;
      setPagina({
        ...anteriores,
        filas: [...anteriores.filas, ...nuevos],
        cursor: siguiente.nextCursor,
        errorMas: null,
      });
    } catch (causa: unknown) {
      if (!sigueVigente()) return;
      setPagina({ ...anteriores, errorMas: mensajeDeError(causa, MENSAJES.inmueblesMas) });
    } finally {
      // Sólo el "cargar más" vigente apaga su indicador; si la generación
      // cambió, el effect de filtros ya lo apagó al empezar.
      if (sigueVigente()) setCargandoMas(false);
    }
  }, [vigente, pagina, cargandoMas, filtros]);

  const eliminar = useCallback(
    async (inmueble: InmuebleResumen): Promise<void> => {
      const filtrosActuales = filtros;
      const recargaActual = recarga;
      const esEsta = (prev: PaginaVigente | null): prev is PaginaVigente =>
        prev !== null && prev.filtros === filtrosActuales && prev.recarga === recargaActual;
      /** Posición original de la fila, para recolocarla en el rollback. */
      const indice = pagina?.filas.findIndex((fila) => fila.id === inmueble.id) ?? -1;

      setAviso(null);
      setErrorAccion(null);
      setEnVuelo((actual) => [...actual, inmueble.id]);
      // Optimista: la fila sale de la lista sin esperar al 204.
      setPagina((prev) => (esEsta(prev) ? quitarFila(prev, inmueble.id) : prev));
      try {
        await eliminarInmueble(inmueble.id);
        setAviso({
          filtros: filtrosActuales,
          recarga: recargaActual,
          texto: `«${inmueble.titulo}» se ha eliminado del portal.`,
        });
      } catch (causa: unknown) {
        // Rollback: la fila vuelve a su sitio y el fallo se ve.
        setPagina((prev) => (esEsta(prev) ? devolverFila(prev, inmueble, indice) : prev));
        setErrorAccion({
          filtros: filtrosActuales,
          recarga: recargaActual,
          texto: mensajeDeError(causa, MENSAJES.borrarInmueble),
        });
      } finally {
        setEnVuelo((actual) => actual.filter((id) => id !== inmueble.id));
      }
    },
    [filtros, recarga, pagina],
  );

  return {
    cargando: !vigente,
    inmuebles: vigente ? pagina.filas : [],
    total: vigente ? pagina.total : null,
    hayMas: vigente && pagina.cursor !== null,
    cargandoMas,
    error: vigente ? pagina.error : null,
    errorMas: vigente ? pagina.errorMas : null,
    aviso:
      aviso !== null && aviso.filtros === filtros && aviso.recarga === recarga ? aviso.texto : null,
    errorAccion:
      errorAccion !== null && errorAccion.filtros === filtros && errorAccion.recarga === recarga
        ? errorAccion.texto
        : null,
    enVuelo,
    aplicar,
    limpiar,
    reintentar,
    cargarMas,
    eliminar,
  };
}

/** Quita la fila del listado y descuenta el total (borrado optimista). */
function quitarFila(pagina: PaginaVigente, id: number): PaginaVigente {
  return {
    ...pagina,
    filas: pagina.filas.filter((fila) => fila.id !== id),
    total: pagina.total === null ? null : Math.max(0, pagina.total - 1),
  };
}

/** Recoloca la fila en su posición original (rollback del optimismo). */
function devolverFila(
  pagina: PaginaVigente,
  inmueble: InmuebleResumen,
  indice: number,
): PaginaVigente {
  const posicion = indice < 0 || indice > pagina.filas.length ? pagina.filas.length : indice;
  return {
    ...pagina,
    filas: [...pagina.filas.slice(0, posicion), inmueble, ...pagina.filas.slice(posicion)],
    total: pagina.total === null ? null : pagina.total + 1,
  };
}
