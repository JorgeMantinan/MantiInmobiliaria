'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { listarInmuebles } from '../api';
import { mensajeDeError, MENSAJES } from '../mensajes';
import type { InmuebleResumen } from '../types';
import { ListadoCargando, ListadoError } from './estados-listado';
import { TarjetaInmueble } from './tarjeta-inmueble';

type EstadoDestacados =
  | Readonly<{ fase: 'ok'; inmuebles: readonly InmuebleResumen[] }>
  | Readonly<{ fase: 'vacio' }>
  | Readonly<{ fase: 'error'; mensaje: string }>;

const ENLACE_SECUNDARIO =
  'inline-block rounded-md border border-borde px-4 py-2 text-sm font-medium text-texto transition-colors outline-none hover:bg-superficie-alta focus:ring-2 focus:ring-acento-nogal';

/** Rejilla de inmuebles destacados del home (6 como máximo). */
export function Destacados() {
  const [estado, setEstado] = useState<EstadoDestacados | null>(null);

  const cargar = useCallback((estaCancelado: () => boolean) => {
    listarInmuebles({ destacado: true }, { limit: 6 })
      .then((pagina) => {
        if (estaCancelado()) return;
        setEstado(
          pagina.content.length === 0
            ? { fase: 'vacio' }
            : { fase: 'ok', inmuebles: pagina.content },
        );
      })
      .catch((errorDeCarga: unknown) => {
        if (estaCancelado()) return;
        setEstado({ fase: 'error', mensaje: mensajeDeError(errorDeCarga, MENSAJES.destacados) });
      });
  }, []);

  useEffect(() => {
    let cancelado = false;
    cargar(() => cancelado);
    return () => {
      cancelado = true;
    };
  }, [cargar]);

  function reintentar() {
    setEstado(null);
    cargar(() => false);
  }

  return (
    <section aria-labelledby="titulo-destacados" className="mx-auto w-full max-w-6xl px-4 py-12 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 id="titulo-destacados" className="font-display text-2xl text-texto">
            Destacados
          </h2>
          <p className="mt-1 text-sm text-texto-suave">
            Una selección de viviendas que merecen un vistazo.
          </p>
        </div>
        <Link href="/inmuebles" className={ENLACE_SECUNDARIO}>
          Ver todas las viviendas
        </Link>
      </div>

      <div className="mt-6">
        {estado === null ? (
          <ListadoCargando />
        ) : estado.fase === 'error' ? (
          <ListadoError mensaje={estado.mensaje} onReintentar={reintentar} />
        ) : estado.fase === 'vacio' ? (
          <div className="rounded-lg border border-borde bg-superficie p-6 text-center">
            <h3 className="font-display text-xl text-texto">Todavía no hay destacados</h3>
            <p className="mt-2 text-sm text-texto-suave">
              Ninguna vivienda está destacada ahora mismo; en el listado completo sí encontrarás
              todas las disponibles.
            </p>
            <div className="mt-4 flex justify-center">
              <Link href="/inmuebles" className={ENLACE_SECUNDARIO}>
                Ver todas las viviendas
              </Link>
            </div>
          </div>
        ) : (
          <ul className="grid list-none gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {estado.inmuebles.map((inmueble) => (
              <li key={inmueble.id} className="h-full">
                <TarjetaInmueble inmueble={inmueble} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
