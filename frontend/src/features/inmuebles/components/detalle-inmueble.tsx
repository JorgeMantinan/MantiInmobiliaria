'use client';

import Link from 'next/link';
import { useCallback, useEffect, useRef, useState } from 'react';
import { esApiError } from '@/lib/api';
import { obtenerInmueble } from '../api';
import { direccionCompleta, ETIQUETAS_ESTADO, ETIQUETAS_TIPO, formatoPrecio } from '../formato';
import { mensajeDeError, MENSAJES } from '../mensajes';
import type { InmuebleDetalle } from '../types';
import { FotoInmueble } from './foto-inmueble';
import { MapaDetalleWrapper } from './mapa-detalle-wrapper';
import { SeccionCaracteristicas } from './seccion-caracteristicas';
import { SeccionErrorFicha } from './seccion-error-ficha';

type EstadoFicha =
  | Readonly<{ fase: 'cargando' }>
  | Readonly<{ fase: 'error'; mensaje: string }>
  | Readonly<{ fase: 'no-encontrado' }>
  | Readonly<{ fase: 'ok'; inmueble: InmuebleDetalle }>;

/** Ficha completa: se carga desde el cliente con estados de carga/error/404. */
export function DetalleInmueble({ id }: Readonly<{ id: number }>) {
  const [estado, setEstado] = useState<EstadoFicha>({ fase: 'cargando' });
  /**
   * Generación de la carga en curso: al cambiar de `id` o al reintentar se
   * incrementa, de modo que una respuesta que llegue con la generación anterior
   * (p. ej. un reintento lento) se descarta en vez de pisar la ficha nueva.
   */
  const generacion = useRef(0);

  const cargar = useCallback(() => {
    const generacionActual = ++generacion.current;
    obtenerInmueble(id)
      .then((inmueble) => {
        if (generacionActual !== generacion.current) return;
        setEstado({ fase: 'ok', inmueble });
      })
      .catch((errorDeCarga: unknown) => {
        if (generacionActual !== generacion.current) return;
        if (esApiError(errorDeCarga) && errorDeCarga.status === 404) {
          setEstado({ fase: 'no-encontrado' });
        } else {
          setEstado({ fase: 'error', mensaje: mensajeDeError(errorDeCarga, MENSAJES.ficha) });
        }
      });
  }, [id]);

  useEffect(() => {
    cargar();
  }, [cargar]);

  function reintentar() {
    setEstado({ fase: 'cargando' });
    cargar();
  }

  if (estado.fase === 'cargando') {
    return (
      <div role="status" className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6">
        <span className="sr-only">Cargando el inmueble…</span>
        <div aria-hidden="true" className="space-y-4">
          <div className="h-8 w-64 animate-pulse rounded-md bg-superficie-alta" />
          <div className="aspect-[4/3] w-full animate-pulse rounded-lg bg-superficie-alta" />
          <div className="h-72 w-full animate-pulse rounded-md border border-borde bg-superficie-alta" />
        </div>
      </div>
    );
  }

  if (estado.fase === 'error' || estado.fase === 'no-encontrado') {
    return (
      <SeccionErrorFicha
        noEncontrado={estado.fase === 'no-encontrado'}
        mensaje={estado.fase === 'error' ? estado.mensaje : ''}
        onReintentar={reintentar}
      />
    );
  }

  const inmueble = estado.inmueble;
  const direccion = direccionCompleta(inmueble);
  const altFoto = `${inmueble.titulo} en ${direccion}`;

  return (
    <article className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6">
      <Link
        href="/inmuebles"
        className="inline-block rounded-md text-sm text-texto-suave outline-none transition-colors hover:text-texto focus:ring-2 focus:ring-acento-nogal"
      >
        ← Volver al listado
      </Link>

      <header className="mt-4 flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap gap-2 text-xs text-texto-suave">
            <span className="rounded-md border border-borde bg-superficie-alta px-2 py-0.5">
              {ETIQUETAS_ESTADO[inmueble.estado]}
            </span>
            <span className="rounded-md border border-borde px-2 py-0.5">
              {ETIQUETAS_TIPO[inmueble.tipo]}
            </span>
          </div>
          <h1 className="mt-2 font-display text-3xl text-texto">{inmueble.titulo}</h1>
          <p className="mt-1 text-sm text-texto">{direccion}</p>
        </div>
        <p className="text-3xl font-semibold text-acento-nogal">{formatoPrecio(inmueble.precio)}</p>
      </header>

      <div className="mt-6">
        <FotoInmueble src={inmueble.imagenUrl} alt={altFoto} />
      </div>

      <SeccionCaracteristicas
        superficieM2={inmueble.superficieM2}
        habitaciones={inmueble.habitaciones}
        banios={inmueble.banios}
        tipo={inmueble.tipo}
      />

      <section className="mt-6" aria-labelledby="titulo-descripcion">
        <h2 id="titulo-descripcion" className="font-display text-xl text-texto">
          Descripción
        </h2>
        <p className="mt-2 whitespace-pre-line text-texto-suave">
          {inmueble.descripcion ?? 'Sin descripción.'}
        </p>
      </section>

      <section className="mt-6" aria-labelledby="titulo-ubicacion">
        <h2 id="titulo-ubicacion" className="font-display text-xl text-texto">
          Ubicación
        </h2>
        <p className="mt-2 text-texto">{direccion}</p>
        <div className="mt-4">
          <MapaDetalleWrapper lat={inmueble.lat} lng={inmueble.lng} titulo={inmueble.titulo} />
        </div>
      </section>

      <div className="mt-8">
        <Link
          href="/inmuebles"
          className="inline-block rounded-md border border-borde px-4 py-2 text-sm font-medium text-texto transition-colors outline-none hover:bg-superficie-alta focus:ring-2 focus:ring-acento-nogal"
        >
          Volver al listado
        </Link>
      </div>
    </article>
  );
}
