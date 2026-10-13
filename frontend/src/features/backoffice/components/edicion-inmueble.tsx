'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { AVISO_ERROR } from '@/components/ui/avisos';
import { Boton } from '@/components/ui/boton';
import { obtenerInmueble } from '@/features/inmuebles/api';
import type { InmuebleDetalle } from '@/features/inmuebles/types';
import { formatoFecha } from '../formato';
import { MENSAJES, mensajeDeError } from '../mensajes';
import { FilasCargando } from './estados-listado';
import { FormularioInmueble } from './formulario-inmueble';

/** Resultado de UNA carga: sólo se pinta si coincide con la recarga actual. */
type ResultadoCarga = Readonly<{
  recarga: number;
  detalle: InmuebleDetalle | null;
  error: string | null;
}>;

const CLASE_ENLACE =
  'inline-block rounded-md border border-borde px-4 py-2 text-sm font-medium text-texto transition-colors outline-none hover:bg-superficie-alta focus:ring-2 focus:ring-acento-nogal';

/**
 * Edición: carga el detalle por el endpoint PÚBLICO (`GET
 * /api/v1/inmuebles/{id}`, que también funciona con el Bearer ADMIN) y pasa el
 * resultado al formulario. Un 404 (inexistente o soft-deleted) se traduce en
 * un aviso con enlace de vuelta al listado.
 */
export function EdicionInmueble({ id }: Readonly<{ id: number }>) {
  /** `null` = todavía no hay resultado de ESTA recarga (⇒ cargando). */
  const [resultado, setResultado] = useState<ResultadoCarga | null>(null);
  const [recarga, setRecarga] = useState(0);

  useEffect(() => {
    let vigente = true;
    obtenerInmueble(id)
      .then((detalle) => {
        if (vigente) setResultado({ recarga, detalle, error: null });
      })
      .catch((causa: unknown) => {
        if (!vigente) return;
        setResultado({
          recarga,
          detalle: null,
          error: mensajeDeError(causa, MENSAJES.detalleInmueble),
        });
      });
    return () => {
      vigente = false;
    };
  }, [id, recarga]);

  const reintentar = useCallback(() => setRecarga((anterior) => anterior + 1), []);

  const vigente = resultado !== null && resultado.recarga === recarga;

  if (!vigente) return <FilasCargando />;

  if (resultado.error !== null || resultado.detalle === null) {
    return (
      <div role="alert" className="rounded-lg border border-borde bg-superficie p-6 text-center">
        <h1 className="font-display text-2xl text-texto">No se puede editar el inmueble</h1>
        <p className={`mt-2 ${AVISO_ERROR}`}>{resultado.error}</p>
        <div className="mt-4 flex flex-wrap justify-center gap-3">
          <Boton onClick={reintentar}>Reintentar</Boton>
          <Link href="/admin/inmuebles" className={CLASE_ENLACE}>
            Volver al listado
          </Link>
        </div>
      </div>
    );
  }

  return (
    <section aria-labelledby="titulo-edicion">
      <h1 id="titulo-edicion" className="font-display text-3xl text-texto">
        Editar inmueble
      </h1>
      <p className="mt-2 text-texto-suave">
        Inmueble #{resultado.detalle.id} · alta el {formatoFecha(resultado.detalle.createdAt)}. El
        PUT reemplaza la representación completa del recurso.
      </p>
      <div className="mt-6">
        <FormularioInmueble inicial={resultado.detalle} />
      </div>
    </section>
  );
}
