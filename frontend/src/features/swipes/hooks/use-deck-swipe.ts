'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import type { InmuebleResumen } from '@/features/inmuebles/types';
import { useSesion } from '@/features/auth/session';
import { esApiError } from '@/lib/api';
import { obtenerDeck, registrarSwipe } from '../api';
import { mensajeDeError, MENSAJES } from '../mensajes';
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
  decidir: (decision: DecisionSwipe) => void;
  reintentar: () => void;
}>;

/**
 * Deck de swipes con POST optimista:
 *
 * - `decidir` saca la carta del deck al instante y lanza el POST en paralelo;
 *   si el POST falla, la carta vuelve al frente y se anuncia el error (salvo
 *   401, que cae a la pantalla de sesión).
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
  // de StrictMode); el resto de cargas se lanzan de forma imperativa.
  const cargadaInicial = useRef(false);
  // Guarda contra el doble effect de la página siguiente (mismas deps).
  const siguienteEnVuelo = useRef(false);

  const anunciarFalloDeCarga = useCallback((fallo: unknown): void => {
    if (esApiError(fallo) && fallo.status === 401) {
      setSesionPerdida(true);
      return;
    }
    setError(mensajeDeError(fallo, MENSAJES.deck));
  }, []);

  const cargarPrimeraPagina = useCallback(async (): Promise<void> => {
    setCargando(true);
    setError(null);
    try {
      const pagina = await obtenerDeck();
      setCartas(pagina.content);
      setCursor(pagina.nextCursor);
      setCargadas(pagina.content.length);
      setDecididas(0);
    } catch (fallo: unknown) {
      anunciarFalloDeCarga(fallo);
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

  // Deck agotado con cursor: se pide la página siguiente en cuanto no quedan
  // cartas. `siguienteEnVuelo` evita duplicar la petición entre renders.
  useEffect(() => {
    if (cargando || cartas.length > 0 || cursor === null) return;
    if (siguienteEnVuelo.current) return;
    siguienteEnVuelo.current = true;
    setCargandoSiguientes(true);
    void obtenerDeck(cursor)
      .then((pagina) => {
        // Página vacía con cursor: corta el bucle y cierra el deck.
        if (pagina.content.length === 0) {
          setCursor(null);
          return;
        }
        setCartas(pagina.content);
        setCargadas((anterior) => anterior + pagina.content.length);
        setCursor(pagina.nextCursor);
      })
      .catch((fallo: unknown) => anunciarFalloDeCarga(fallo))
      .finally(() => {
        siguienteEnVuelo.current = false;
        setCargandoSiguientes(false);
      });
  }, [cargando, cartas.length, cursor, anunciarFalloDeCarga]);

  const decidir = useCallback(
    (decision: DecisionSwipe): void => {
      const carta = cartas[0];
      if (carta === undefined) return;

      setDireccionSalida(decision);
      setCartas((anteriores) => anteriores.slice(1));
      setDecididas((anterior) => anterior + 1);
      setErrorDecision(null);

      void registrarSwipe(carta.id, decision).catch((fallo: unknown) => {
        if (esApiError(fallo) && fallo.status === 401) {
          setSesionPerdida(true);
          return;
        }
        // Reintegro optimista: la carta recupera su sitio en el deck.
        setCartas((anteriores) => [carta, ...anteriores]);
        setDecididas((anterior) => Math.max(0, anterior - 1));
        setErrorDecision(mensajeDeError(fallo, MENSAJES.decision));
      });
    },
    [cartas],
  );

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
