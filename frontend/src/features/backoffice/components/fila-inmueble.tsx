'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { Boton } from '@/components/ui/boton';
import { formatoPrecio } from '@/features/inmuebles/formato';
import type { InmuebleResumen } from '@/features/inmuebles/types';
import { BadgeDestacado, BadgeEstado } from './badge-estado';

type FilaInmuebleProps = Readonly<{
  inmueble: InmuebleResumen;
  /** true ⇒ hay un DELETE en vuelo para esta fila (acciones deshabilitadas). */
  ocupado: boolean;
  onEliminar: (inmueble: InmuebleResumen) => void;
}>;

const CLASE_ACCION =
  'rounded-md border border-borde px-4 py-2 text-sm font-medium text-texto transition-colors outline-none hover:bg-superficie-alta focus:ring-2 focus:ring-acento-nogal disabled:cursor-not-allowed disabled:opacity-60';

/** Fila del backoffice: datos + badges + Editar y Eliminar en dos pasos. */
export function FilaInmueble({ inmueble, ocupado, onEliminar }: FilaInmuebleProps) {
  const { id, titulo, precio, tipo, estado, municipio, provincia, superficieM2, destacado } =
    inmueble;
  /** true ⇒ esperando la confirmación de borrado (sin `confirm()`). */
  const [confirmando, setConfirmando] = useState(false);
  /** Botón que abrió la confirmación: ahí vuelve el foco al cerrarla. */
  const gatillo = useRef<HTMLButtonElement | null>(null);
  /** Última confirmación vista: detecta el cierre sin robar foco al montar. */
  const confirmacionPrevia = useRef(false);

  /**
   * Al cerrar la confirmación (cancelar o confirmar) el foco vuelve al
   * gatillo. Va en un effect y no síncrono en el onClick: en el momento del
   * clic el gatillo sigue oculto (`display: none`) y `focus()` sería no-op.
   */
  useEffect(() => {
    if (confirmacionPrevia.current && !confirmando) {
      gatillo.current?.focus();
    }
    confirmacionPrevia.current = confirmando;
  }, [confirmando]);

  return (
    <li className="rounded-lg border border-borde bg-superficie p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="font-display text-lg text-texto">{titulo}</h2>
          <p className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-sm text-texto-suave">
            <span className="font-medium text-acento-nogal">{formatoPrecio(precio)}</span>
            <span>
              {tipo.charAt(0) + tipo.slice(1).toLowerCase()}
              {superficieM2 !== null ? ` · ${superficieM2} m²` : ''}
            </span>
            <span>
              {municipio}
              {provincia !== '' && provincia !== municipio ? `, ${provincia}` : ''}
            </span>
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-2">
          <BadgeEstado estado={estado} />
          {destacado ? <BadgeDestacado /> : null}
        </div>
      </div>

      <div className="mt-4 border-t border-borde pt-4">
        {/*
          Las acciones NO se desmontan al confirmar (sólo se ocultan): así el
          botón que abrió la confirmación conserva su nodo en el DOM y al
          cancelar puede volver a recibir el foco.
        */}
        <div className={confirmando ? 'hidden' : 'flex flex-wrap gap-2'}>
          <Link href={`/admin/inmuebles/${id}`} className={CLASE_ACCION}>
            Editar
          </Link>
          <Boton
            variante="secundario"
            disabled={ocupado}
            onClick={(evento) => {
              gatillo.current = evento.currentTarget;
              setConfirmando(true);
            }}
          >
            Eliminar
          </Boton>
        </div>
        {confirmando ? (
          <div
            role="group"
            aria-label="Confirmar: Eliminar"
            className="flex flex-wrap items-center gap-3 rounded-md border border-acento-nogal bg-superficie-alta p-3"
          >
            <p role="status" aria-live="polite" className="text-sm text-texto">
              ¿Seguro? <strong>{titulo}</strong> dejará de mostrarse en el portal.
            </p>
            <div className="flex gap-2">
              <Boton
                onClick={() => {
                  onEliminar(inmueble);
                  // El effect de arriba devuelve el foco al gatillo al cerrar.
                  setConfirmando(false);
                }}
              >
                Sí, eliminar
              </Boton>
              <Boton
                variante="secundario"
                autoFocus
                onClick={() => {
                  setConfirmando(false);
                }}
              >
                Cancelar
              </Boton>
            </div>
          </div>
        ) : null}
      </div>
    </li>
  );
}
