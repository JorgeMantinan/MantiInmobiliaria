'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import type { InmuebleResumen } from '@/features/inmuebles/types';
import { useSesion } from '@/features/auth/session';
import { esSinSesion, mensajeDeError } from '@/lib/errores';
import { obtenerDeck, registrarSwipe } from '../api';
import { MENSAJES } from '../mensajes';
import type { DecisionSwipe } from '../types';

/** Posición de la carta actual dentro de las cartas ya cargadas. */
type PosicionCarta = Readonly<{ actual: number; total: number }>;

export type EstadoDeck = Readonly<{
  /** Primera página del deck todavía en vuelo (o sesión aún restaurándose). */
  cargando: boolean;
  error: string | null;
  cartas: readonly InmuebleResumen[];
  /** Dirección de salida de la última carta (la anima `AnimatePresence`). */
  direccionSalida: DecisionSwipe;
  posicion: PosicionCarta;
  /** Hay página siguiente (`nextCursor`) y no queda ninguna carta en memoria. */
  pendienteSiguiente: boolean;
  cargandoSiguientes: boolean;
  /** Fallo del POST optimista: la carta ha vuelto al frente del deck. */
  errorDecision: string | null;
  /** Sin sesión (401 o `useSesion` sin usuario): no se llama a la API. */
  sinSesion: boolean;
  /** Sin cartas ni página siguiente: deck agotado. */
  finDeDeck: boolean;
}>;

export type AccionesDeck = Readonly<{
  /** Decide sobre la carta SUPERIOR; ignora ids que ya no lo son. */
  decidir: (inmuebleId: number, decision: DecisionSwipe) => void;
  reintentar: () => void;
}>;

/**
 * Deck de swipes con POST optimista y protección de carreras:
 *
 * - `decidir(inmuebleId, decision)` sólo actúa si el id sigue siendo la carta
 *   superior (`cartasRef`) y no tiene ya un POST en vuelo (`enVueloRef`):
 *   una segunda decisión mientras la anterior aún sale no elimina otra carta
 *   sin su propio POST.
 * - Si el POST falla, la carta vuelve al frente con merge SIN pisar la página
 *   siguiente que pudiera estar cargando en paralelo (dedupe por id) y se
 *   anuncia el error (salvo 401, que cae a la pantalla de sesión).
 * - Cada recarga invalida por generación las respuestas anteriores, al estilo
 *   de `use-listado-inmuebles`.
 * - Agotado el deck se pide la página siguiente con el `nextCursor`; sin
 *   cursor, `finDeDeck` queda en true (valor derivado, sin efectos).
 * - Con sesión anónima no se lanza ninguna petición.
 */
export function useDeckSwipe(): EstadoDeck & AccionesDeck {
  const { usuario, cargando: cargandoSesion } = useSesion();

  const [cartas, setCartas] = useState<readonly InmuebleResumen[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [cargadas, setCargadas] = useState(0);
  const [decididas, setDecididas] = useState(0);
  const [cargando, setCargando] = useState(true);
  const [cargandoSiguientes, setCargandoSiguientes] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [errorDecision, setErrorDecision] = useState<string | null>(null);
  const [sesionPerdida, setSesionPerdida] = useState(false);
  const [direccionSalida, setDireccionSalida] = useState<DecisionSwipe>('LIKE');

  // Sólo la primera página se pide desde el effect (evita el doble montaje
  // de StrictMode); el resto de cargas se lanza de forma imperativa.
  const cargadaInicial = useRef(false);
  // Guarda contra el doble effect de la página siguiente (mismas deps).
  const siguienteEnVuelo = useRef(false);
  // Espejo de `cartas` para leer la carta superior DESDE el handler de evento
  // sin depender del closure del render (cierre obsoleto).
  const cartasRef = useRef<readonly InmuebleResumen[]>([]);
  // Ids con un POST de decisión en vuelo: no se repite mientras esté activo
  // y se libera en `finally` (el reintento tras rollback vuelve a permitirlo).
  const enVueloRef = useRef<ReadonlySet<number>>(new Set());
  // Cada recarga nueva invalida las respuestas anteriores (carreras).
  const generacion = useRef(0);

  useEffect(() => {
    cartasRef.current = cartas;
  }, [cartas]);

  const anunciarFalloDeCarga = useCallback((fallo: unknown): void => {
    if (esSinSesion(fallo)) {
      setSesionPerdida(true);
      return;
    }
    setError(mensajeDeError(fallo, MENSAJES.deck));
  }, []);

  const cargarPrimeraPagina = useCallback(async (): Promise<void> => {
    const generacionActual = ++generacion.current;
    setCargando(true);
    setError(null);
    try {
      const pagina = await obtenerDeck();
      // Respuesta obsoleta: otra recarga más reciente ya mandó.
      if (generacionActual !== generacion.current) return;
      setCartas(pagina.content);
      setCursor(pagina.nextCursor);
      setCargadas(pagina.content.length);
      setDecididas(0);
    } catch (fallo: unknown) {
      if (generacionActual === generacion.current) anunciarFalloDeCarga(fallo);
    } finally {
      if (generacionActual === generacion.current) setCargando(false);
    }
  }, [anunciarFalloDeCarga]);

  const sinSesion = sesionPerdida || (!cargandoSesion && usuario === null);

  // Primera carga: sólo cuando la sesión está resuelta y hay usuario.
  useEffect(() => {
    if (cargandoSesion || sinSesion || cargadaInicial.current) return;
    cargadaInicial.current = true;
    void cargarPrimeraPagina();
  }, [cargandoSesion, sinSesion, cargarPrimeraPagina]);

  // Deck agotado con cursor: se pide la página siguiente en cuanto no quedan
  // cartas. `siguienteEnVuelo` evita duplicar la petición entre renders y la
  // generación descarta respuestas de recargas ya invalidadas.
  useEffect(() => {
    if (cargando || sinSesion || cartas.length > 0 || cursor === null) return;
    if (siguienteEnVuelo.current) return;
    siguienteEnVuelo.current = true;
    const generacionAlLanzar = generacion.current;
    setCargandoSiguientes(true);
    void obtenerDeck(cursor)
      .then((pagina) => {
        if (generacionAlLanzar !== generacion.current) return;
        // Página vacía con cursor: corta el bucle y cierra el deck.
        if (pagina.content.length === 0) {
          setCursor(null);
          return;
        }
        // Fusión con dedupe: nunca sustituye lo que haya en memoria (una
        // carta reinsertada por un POST fallido sobrevive a esta carga).
        setCartas((anteriores) =>
          anteriores.length === 0
            ? pagina.content
            : [
                ...anteriores,
                ...pagina.content.filter(
                  (nueva) => !anteriores.some((anterior) => anterior.id === nueva.id),
                ),
              ],
        );
        setCargadas((anterior) => anterior + pagina.content.length);
        setCursor(pagina.nextCursor);
      })
      .catch((fallo: unknown) => {
        if (generacionAlLanzar === generacion.current) anunciarFalloDeCarga(fallo);
      })
      .finally(() => {
        siguienteEnVuelo.current = false;
        setCargandoSiguientes(false);
      });
  }, [cargando, sinSesion, cartas.length, cursor, anunciarFalloDeCarga]);

  const decidir = useCallback((inmuebleId: number, decision: DecisionSwipe): void => {
    const carta = cartasRef.current[0];
    // Carta obsoleta (quien llamó ya no es la superior) o POST en vuelo:
    // no se toca el estado ni se repite la petición.
    if (carta === undefined || carta.id !== inmuebleId) return;
    if (enVueloRef.current.has(inmuebleId)) return;

    enVueloRef.current = new Set(enVueloRef.current).add(inmuebleId);
    const generacionAlDecidir = generacion.current;
    setDireccionSalida(decision);
    setCartas((anteriores) => anteriores.slice(1));
    setDecididas((anterior) => anterior + 1);
    setErrorDecision(null);

    void registrarSwipe(inmuebleId, decision)
      .catch((fallo: unknown) => {
        // La recarga invalidó este deck: nada que reintegrar.
        if (generacionAlDecidir !== generacion.current) return;
        if (esSinSesion(fallo)) {
          setSesionPerdida(true);
          return;
        }
        // Reintegro optimista con merge: recupera su sitio aunque otra
        // decisión o una página siguiente hayan tocado el mazo entretanto.
        setCartas((anteriores) => [carta, ...anteriores]);
        setDecididas((anterior) => Math.max(0, anterior - 1));
        setErrorDecision(mensajeDeError(fallo, MENSAJES.decision));
      })
      .finally(() => {
        const siguiente = new Set(enVueloRef.current);
        siguiente.delete(inmuebleId);
        enVueloRef.current = siguiente;
      });
  }, []);

  const reintentar = useCallback((): void => {
    void cargarPrimeraPagina();
  }, [cargarPrimeraPagina]);

  const pendienteSiguiente = !cargando && cartas.length === 0 && cursor !== null && error === null;
  const finDeDeck =
    !cargando && !cargandoSiguientes && cartas.length === 0 && cursor === null && error === null;

  return {
    cargando,
    error,
    cartas,
    direccionSalida,
    posicion: { actual: decididas + 1, total: cargadas },
    pendienteSiguiente,
    cargandoSiguientes,
    errorDecision,
    sinSesion,
    finDeDeck,
    decidir,
    reintentar,
  };
}
