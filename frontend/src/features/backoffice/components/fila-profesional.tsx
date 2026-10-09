'use client';

import { useState } from 'react';
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

/** Acciones disponibles según el estado actual (PENDIENTE⇒ambas). */
const ACCIONES: Readonly<Record<EstadoProfesional, readonly EstadoModeracion[]>> = {
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
        {confirmacion === null ? (
          <div className="flex flex-wrap gap-2">
            {ACCIONES[estado].map((destino) => (
              <Boton
                key={destino}
                variante={destino === 'ACTIVO' ? 'primario' : 'secundario'}
                disabled={ocupado}
                onClick={() => setConfirmacion(destino)}
              >
                {ETIQUETAS_ACCION[destino]}
              </Boton>
            ))}
          </div>
        ) : (
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
                  setConfirmacion(null);
                }}
              >
                Sí, {ETIQUETAS_ACCION[confirmacion].toLowerCase()}
              </Boton>
              <Boton variante="secundario" onClick={() => setConfirmacion(null)}>
                Cancelar
              </Boton>
            </div>
          </div>
        )}
      </div>
    </li>
  );
}
