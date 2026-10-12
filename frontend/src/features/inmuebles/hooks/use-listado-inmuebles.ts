'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { listarInmuebles } from '../api';
import { leerFiltros, leerVista, queryListado } from '../filtros';
import { mensajeDeError, MENSAJES } from '../mensajes';
import type { FiltrosInmuebles, InmuebleResumen, VistaListado } from '../types';

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
  /**
   * Query de la URL SIN `vista`: sirve de `key` para reiniciar el formulario
   * y de dependencia de la búsqueda (alternar Lista/Mapa no debe refetchear).
   */
  consulta: string;
  /** Vista activa (`?vista=mapa`); sólo cambia la presentación, no los datos. */
  vista: VistaListado;
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
  /** Alterna Lista/Mapa escribiendo `?vista=mapa` (compartible) en la URL. */
  cambiarVista: (vista: VistaListado) => void;
}>;

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
    error: mensajeDeError(error, MENSAJES.listado),
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
 * `searchParams`), paginación por cursor que va acumulando, vista Lista/Mapa
 * (`?vista=mapa`) y estados de carga / error / vacío. Toda la lógica de datos
 * vive aquí; la vista sólo pinta y llama a las acciones.
 */
export function useListadoInmuebles(): ListadoEstado & ListadoAcciones {
  const router = useRouter();
  const searchParams = useSearchParams();
  /**
   * La búsqueda depende SOLO de los filtros: `vista` cambia la presentación,
   * no los datos, y no debe disparar un nuevo fetch al alternar Lista/Mapa.
   */
  const consulta = useMemo(() => {
    const params = new URLSearchParams(searchParams.toString());
    params.delete('vista');
    return params.toString();
  }, [searchParams]);
  const filtros = useMemo(() => leerFiltros(new URLSearchParams(consulta)), [consulta]);
  const vista = useMemo(() => leerVista(searchParams), [searchParams]);

  const [resultado, setResultado] = useState<ResultadoBusqueda | null>(null);
  const [cargandoMas, setCargandoMas] = useState(false);
  const idsCargados = useRef<ReadonlySet<number>>(new Set());
  /**
   * Contador de "búsquedas en vuelo". Se incrementa al cambiar los filtros y
   * al reintentar: cualquier respuesta que llegue con una generación antigua
   * pertenece a una búsqueda ya invalidada y se descarta. Sin esta guarda, una
   * respuesta obsoleta podría pisar el estado actual y dejar `cargando`/`cargandoMas`
   * clavados (o mezclar páginas de filtros distintos).
   */
  const generacion = useRef(0);

  useEffect(() => {
    const generacionActual = ++generacion.current;
    // Al cambiar de filtros empieza una página nueva: se limpian los ids
    // deduplicados y el "cargar más" anterior deja de ser válido (si seguía en
    // vuelo, su bloque verá otra generación y no tocará el estado).
    idsCargados.current = new Set();
    let vigente = true;
    traerPrimeraPagina(filtros).then((resultadoNuevo) => {
      if (!vigente || generacionActual !== generacion.current) return;
      idsCargados.current = idsDe(resultadoNuevo);
      // El reset de `cargandoMas` va en este callback y no en el cuerpo del
      // effect: React prohíbe setState síncrono ahí. Mientras no haya resultado
      // de ESTOS filtros el "cargar más" ni siquiera se pinta (`cargando`), así
      // que no se abre ninguna ventana incoherente.
      setCargandoMas(false);
      setResultado(resultadoNuevo);
    });
    return () => {
      vigente = false;
    };
  }, [filtros]);

  const paginaActual = resultado !== null && resultado.filtros === filtros ? resultado : null;

  const aplicar = useCallback(
    (nuevos: FiltrosInmuebles) => {
      // La vista se conserva: aplicar un filtro desde el mapa no debe expulsar
      // al usuario de la vista que ha elegido.
      router.replace(`/inmuebles${queryListado(nuevos, vista)}`, { scroll: false });
    },
    [router, vista],
  );

  const limpiar = useCallback(() => {
    router.replace(`/inmuebles${queryListado({}, vista)}`, { scroll: false });
  }, [router, vista]);

  const cambiarVista = useCallback(
    (nueva: VistaListado) => {
      if (nueva === vista) return;
      // Mismos filtros, otra presentación: al ser la query de filtros idéntica
      // el hook no re-busca (el mapa deriva de `inmuebles` ya cargados).
      router.replace(`/inmuebles${queryListado(filtros, nueva)}`, { scroll: false });
    },
    [router, filtros, vista],
  );

  const reintentar = useCallback(() => {
    // Invalida cualquier carga en vuelo (un reintento anterior o la primera
    // página del effect) antes de disparar la nueva: sólo aplica la última.
    const generacionActual = ++generacion.current;
    setResultado(null);
    setCargandoMas(false);
    void traerPrimeraPagina(filtros).then((resultadoNuevo) => {
      if (generacionActual !== generacion.current) return;
      idsCargados.current = idsDe(resultadoNuevo);
      setResultado(resultadoNuevo);
    });
  }, [filtros]);

  const cargarMas = useCallback(async () => {
    if (paginaActual === null || paginaActual.cursor === null || cargandoMas) return;
    const anteriores = paginaActual;
    const filtrosAlLanzar = filtros;
    const generacionAlLanzar = generacion.current;
    /** Si los filtros cambiaron (u otro reintento corrió), esta página ya no pinta. */
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
      setResultado({
        ...anteriores,
        inmuebles: [...anteriores.inmuebles, ...nuevos],
        cursor: siguiente.nextCursor,
        errorMas: null,
      });
    } catch (errorDeCarga: unknown) {
      if (!sigueVigente()) return;
      setResultado({ ...anteriores, errorMas: mensajeDeError(errorDeCarga, MENSAJES.listado) });
    } finally {
      // Sólo el "cargar más" vigente apaga su propio indicador; si la
      // generación cambió, el effect de filtros ya lo apagó al empezar.
      if (sigueVigente()) setCargandoMas(false);
    }
  }, [paginaActual, cargandoMas, filtros]);

  return {
    filtros,
    consulta,
    vista,
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
    cambiarVista,
  };
}
