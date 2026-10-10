'use client';

import { useEffect, useRef, useState } from 'react';
import { Boton } from '@/components/ui/boton';
import { etiquetaEstado, formatoFecha } from '../formato';
import type { EstadoModeracion, EstadoProfesional, ProfesionalAdmin } from '../types';
import { BadgeEstado, ChipsGremios } from './badge-estado';

type FilaProfesionalProps = Readonly<{
  profesional: ProfesionalAdmin;
  /** true ⇒ hay un PATCH en vuelo para esta fila (botones deshabilitados). */
  ocupado: boolean;
  onCambio: (profesional: ProfesionalAdmin, destino: EstadoModeracion) => void;
}>;

/**
 * Acciones disponibles según el estado actual (PENDIENTE⇒ambas). Parcial a
 * propósito: un estado que el backend aún no conoce no rompe la fila.
 */
const ACCIONES: Partial<Readonly<Record<EstadoProfesional, readonly EstadoModeracion[]>>> = {
  PENDIENTE: ['ACTIVO', 'RECHAZADO'],
  ACTIVO: ['RECHAZADO'],
  RECHAZADO: ['ACTIVO'],
};

const ETIQUETAS_ACCION: Readonly<Record<EstadoModeracion, string>> = {
  ACTIVO: 'Aprobar',
  RECHAZADO: 'Rechazar',
};

/** Fila de moderación: datos + badge + acciones en dos pasos (sin `confirm()`). */
export function FilaProfesional({ profesional, ocupado, onCambio }: FilaProfesionalProps) {
  const { nombre, email, telefono, municipio, gremios, estado, creadoEn } = profesional;
  /** Destino elegido en el primer clic; `null` ⇒ esperando la primera acción. */
  const [confirmacion, setConfirmacion] = useState<EstadoModeracion | null>(null);
  /** Botón que abrió la confirmación: ahí vuelve el foco al cerrarla. */
  const gatillo = useRef<HTMLButtonElement | null>(null);
  /** Última confirmación vista: detecta el cierre (null) sin robar foco al montar. */
  const confirmacionPrevia = useRef<EstadoModeracion | null>(null);

  /**
   * Al cerrar la confirmación (cancelar o confirmar) el foco vuelve al gatillo.
   * Va en un effect y no sincrónicamente en el onClick: en el momento del clic
   * el gatillo sigue oculto (`display: none`) y `focus()` sería no-op en
   * navegador real. Si la fila se ha ido ya (triunfo del PATCH), el nodo está
   * desmontado y el foco cae sin error.
   */
  useEffect(() => {
    if (confirmacionPrevia.current !== null && confirmacion === null) {
      gatillo.current?.focus();
    }
    confirmacionPrevia.current = confirmacion;
  }, [confirmacion]);

  return (
    <li className="rounded-lg border border-borde bg-superficie p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="font-display text-lg text-texto">{nombre}</h2>
          <p className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-sm text-texto-suave">
            <a
              href={`mailto:${email}`}
              className="underline underline-offset-4 outline-none hover:text-acento-hover focus:ring-2 focus:ring-acento-nogal"
            >
              {email}
            </a>
            {telefono !== '' ? (
              <a
                href={`tel:${telefono}`}
                className="outline-none hover:text-acento-hover focus:ring-2 focus:ring-acento-nogal"
              >
                {telefono}
              </a>
            ) : null}
            <span>{municipio}</span>
          </p>
        </div>
        <BadgeEstado estado={estado} />
      </div>

      <div className="mt-3">
        <ChipsGremios gremios={gremios} />
      </div>

      <p className="mt-3 text-sm text-texto-suave">Alta: {formatoFecha(creadoEn)}</p>

      <div className="mt-4 border-t border-borde pt-4">
        {/*
          Las acciones NO se desmontan al confirmar (sólo se ocultan): así el
          botón que abrió la confirmación conserva su nodo en el DOM y al
          cancelar puede volver a recibir el foco.
        */}
        <div className={confirmacion === null ? 'flex flex-wrap gap-2' : 'hidden'}>
          {(ACCIONES[estado] ?? []).map((destino) => (
            <Boton
              key={destino}
              variante={destino === 'ACTIVO' ? 'primario' : 'secundario'}
              disabled={ocupado}
              onClick={(evento) => {
                gatillo.current = evento.currentTarget;
                setConfirmacion(destino);
              }}
            >
              {ETIQUETAS_ACCION[destino]}
            </Boton>
          ))}
        </div>
        {confirmacion !== null ? (
          <div
            role="group"
            aria-label={`Confirmar: ${ETIQUETAS_ACCION[confirmacion]}`}
            className="flex flex-wrap items-center gap-3 rounded-md border border-acento-nogal bg-superficie-alta p-3"
          >
            <p role="status" aria-live="polite" className="text-sm text-texto">
              ¿Seguro? {nombre} pasará a <strong>{etiquetaEstado(confirmacion)}</strong>.
            </p>
            <div className="flex gap-2">
              <Boton
                onClick={() => {
                  onCambio(profesional, confirmacion);
                  // El effect de arriba devuelve el foco al gatillo al cerrar.
                  setConfirmacion(null);
                }}
              >
                Sí, {ETIQUETAS_ACCION[confirmacion].toLowerCase()}
              </Boton>
              <Boton
                variante="secundario"
                autoFocus
                onClick={() => {
                  setConfirmacion(null);
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
