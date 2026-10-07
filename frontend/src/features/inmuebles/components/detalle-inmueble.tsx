'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { esApiError } from '@/lib/api';
import { obtenerInmueble } from '../api';
import {
  direccionCompleta,
  ETIQUETAS_ESTADO,
  ETIQUETAS_TIPO,
  formatoPrecio,
} from '../formato';
import type { InmuebleDetalle } from '../types';
import { FotoInmueble } from './foto-inmueble';
import { MapaDetalleWrapper } from './mapa-detalle-wrapper';

type EstadoFicha =
  | Readonly<{ fase: 'cargando' }>
  | Readonly<{ fase: 'error'; mensaje: string }>
  | Readonly<{ fase: 'no-encontrado' }>
  | Readonly<{ fase: 'ok'; inmueble: InmuebleDetalle }>;

type CaracteristicaProps = Readonly<{ etiqueta: string; valor: string }>;

function Caracteristica({ etiqueta, valor }: CaracteristicaProps) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-texto-suave">{etiqueta}</dt>
      <dd className="mt-0.5 font-medium text-texto">{valor}</dd>
    </div>
  );
}

function valorOpcion(valor: number | null, formato: (v: number) => string): string {
  return valor === null ? '—' : formato(valor);
}

function mensajeDeError(error: unknown): string {
  return error instanceof Error ? error.message : 'No se ha podido cargar el inmueble.';
}

/** Ficha completa: se carga desde el cliente con estados de carga/error/404. */
export function DetalleInmueble({ id }: Readonly<{ id: number }>) {
  const [estado, setEstado] = useState<EstadoFicha>({ fase: 'cargando' });

  const cargar = useCallback(
    (estaCancelado: () => boolean) => {
      obtenerInmueble(id)
        .then((inmueble) => {
          if (!estaCancelado()) setEstado({ fase: 'ok', inmueble });
        })
        .catch((errorDeCarga: unknown) => {
          if (estaCancelado()) return;
          if (esApiError(errorDeCarga) && errorDeCarga.status === 404) {
            setEstado({ fase: 'no-encontrado' });
          } else {
            setEstado({ fase: 'error', mensaje: mensajeDeError(errorDeCarga) });
          }
        });
    },
    [id],
  );

  useEffect(() => {
    let cancelado = false;
    cargar(() => cancelado);
    return () => {
      cancelado = true;
    };
  }, [cargar]);

  function reintentar() {
    setEstado({ fase: 'cargando' });
    cargar(() => false);
  }

  if (estado.fase === 'cargando') {
    return (
      <div role="status" aria-live="polite" className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6">
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
    const noEncontrado = estado.fase === 'no-encontrado';
    return (
      <div className="mx-auto flex w-full max-w-md flex-1 items-center justify-center px-4 py-12">
        <section className="w-full rounded-lg border border-borde bg-superficie p-6 text-center">
          <h1 className="font-display text-2xl text-texto">
            {noEncontrado ? 'Inmueble no encontrado' : 'No se ha podido cargar la ficha'}
          </h1>
          <p role="alert" aria-live="polite" className="mt-2 text-sm text-texto-suave">
            {noEncontrado
              ? 'Puede que se haya retirado el anuncio o que el enlace no sea correcto.'
              : estado.mensaje}
          </p>
          {!noEncontrado ? (
            <div className="mt-6 flex flex-col gap-3">
              <button
                type="button"
                onClick={reintentar}
                className="rounded-md bg-acento-nogal px-4 py-2 text-sm font-medium text-superficie transition-colors outline-none hover:bg-acento-hover focus:ring-2 focus:ring-acento-nogal"
              >
                Reintentar
              </button>
            </div>
          ) : null}
          <Link
            href="/inmuebles"
            className="mt-4 inline-block rounded-md border border-borde px-4 py-2 text-sm font-medium text-texto transition-colors outline-none hover:bg-superficie-alta focus:ring-2 focus:ring-acento-nogal"
          >
            Ver todas las viviendas
          </Link>
        </section>
      </div>
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

      <dl className="mt-6 grid grid-cols-2 gap-4 rounded-lg border border-borde bg-superficie p-5 sm:grid-cols-4">
        <Caracteristica
          etiqueta="Superficie"
          valor={valorOpcion(inmueble.superficieM2, (v) => `${v} m²`)}
        />
        <Caracteristica
          etiqueta="Habitaciones"
          valor={valorOpcion(inmueble.habitaciones, (v) => String(v))}
        />
        <Caracteristica etiqueta="Baños" valor={valorOpcion(inmueble.banios, (v) => String(v))} />
        <Caracteristica etiqueta="Tipo" valor={ETIQUETAS_TIPO[inmueble.tipo]} />
      </dl>

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
