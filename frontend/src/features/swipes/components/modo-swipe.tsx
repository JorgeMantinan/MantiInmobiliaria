'use client';

import Link from 'next/link';
import { AnimatePresence } from 'framer-motion';
import { useDeckSwipe } from '../hooks/use-deck-swipe';
import { BotonesDecision } from './botones-decision';
import { CartaSwipe } from './carta-swipe';
import { CtaSesion } from './cta-sesion';

/** Sólo las 2-3 cartas superiores se montan; el resto espera en el deck. */
const CARTAS_VISIBLES = 3;

const ENLACE_PRIMARIO =
  'rounded-md bg-acento-nogal px-4 py-2 text-sm font-medium text-superficie transition-colors outline-none focus:ring-2 focus:ring-acento-nogal hover:bg-acento-hover';

const ENLACE_SECUNDARIO =
  'rounded-md border border-borde px-4 py-2 text-sm font-medium text-texto transition-colors outline-none focus:ring-2 focus:ring-acento-nogal hover:bg-superficie-alta';

const BOTON_PRIMARIO =
  'rounded-md bg-acento-nogal px-4 py-2 text-sm font-medium text-superficie transition-colors outline-none focus:ring-2 focus:ring-acento-nogal hover:bg-acento-hover';

/**
 * Página del modo swipe: deck apilado con arrastre + botones accesibles,
 * POST optimista por decisión y paginación del deck por `nextCursor`.
 * El área del deck permanece montada mientras carga la siguiente página o
 * termina, para que la carta saliente pueda animarse.
 */
export function ModoSwipe() {
  const deck = useDeckSwipe();
  const hayCarta = deck.cartas.length > 0;
  const anunciando = hayCarta ? `Carta ${deck.posicion.actual} de ${deck.posicion.total}` : '';

  return (
    <section
      aria-labelledby="titulo-swipe"
      className="mx-auto flex w-full max-w-6xl flex-1 flex-col items-center px-4 py-8 sm:px-6"
    >
      <header className="mb-4 w-full max-w-md text-center">
        <h1 id="titulo-swipe" className="font-display text-3xl text-texto">
          Modo swipe
        </h1>
        <p className="mt-1 text-sm text-texto-suave">
          Arrastra a la derecha para guardar y a la izquierda para descartar; también puedes usar
          los botones.
        </p>
      </header>

      {deck.sinSesion ? (
        <CtaSesion
          titulo="Inicia sesión para guardar tus favoritos"
          descripcion="Con tu cuenta podrás revisar viviendas y llevar la cuenta de tus favoritos."
        />
      ) : deck.cargando ? (
        <div role="status" className="w-full max-w-md">
          <span className="sr-only">Cargando viviendas para revisar…</span>
          <div
            aria-hidden="true"
            className="aspect-[4/5] w-full animate-pulse rounded-lg border border-borde bg-superficie-alta"
          />
        </div>
      ) : deck.error !== null ? (
        <div className="w-full max-w-md rounded-lg border border-borde bg-superficie p-6 text-center">
          <div role="alert">
            <h2 className="font-display text-xl text-texto">
              No se han podido cargar las viviendas
            </h2>
            <p className="mt-2 text-sm text-texto-suave">{deck.error}</p>
          </div>
          <div className="mt-4 flex justify-center">
            <button type="button" onClick={deck.reintentar} className={BOTON_PRIMARIO}>
              Reintentar
            </button>
          </div>
        </div>
      ) : (
        <>
          <p
            role="status"
            aria-live="polite"
            className={hayCarta ? 'mb-3 text-sm text-texto-suave' : 'sr-only'}
          >
            {anunciando}
          </p>

          <div className="grid w-full max-w-md">
            <AnimatePresence custom={deck.direccionSalida}>
              {deck.cartas.slice(0, CARTAS_VISIBLES).map((inmueble, indice) => (
                <CartaSwipe
                  key={inmueble.id}
                  inmueble={inmueble}
                  esSuperior={indice === 0}
                  onDecidir={deck.decidir}
                />
              ))}
            </AnimatePresence>
          </div>

          {deck.errorDecision !== null ? (
            <p role="alert" className="mt-4 max-w-md text-center text-sm text-texto">
              {deck.errorDecision}
            </p>
          ) : null}

          <BotonesDecision onDecidir={deck.decidir} deshabilitados={!hayCarta} />

          {deck.pendienteSiguiente || deck.cargandoSiguientes ? (
            <div role="status" className="mt-4 w-full max-w-md text-center text-sm text-texto-suave">
              Cargando siguientes viviendas…
            </div>
          ) : null}

          {deck.finDeDeck ? (
            <div className="mt-4 w-full max-w-md rounded-lg border border-borde bg-superficie p-6 text-center">
              <h2 className="font-display text-2xl text-texto">
                ¡No quedan viviendas por revisar!
              </h2>
              <p className="mt-2 text-sm text-texto-suave">
                Vuelve más tarde: cada día publicamos anuncios nuevos.
              </p>
              <div className="mt-6 flex flex-col gap-3">
                <Link href="/favoritos" className={ENLACE_PRIMARIO}>
                  Ver favoritos
                </Link>
                <Link href="/inmuebles" className={ENLACE_SECUNDARIO}>
                  Explorar viviendas
                </Link>
              </div>
            </div>
          ) : null}
        </>
      )}
    </section>
  );
}
