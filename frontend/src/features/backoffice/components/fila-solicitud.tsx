'use client';

import { useState } from 'react';
import { formatoFecha } from '../formato';
import type { SolicitudAdmin } from '../types';
import { BadgeEstado, ChipsGremios } from './badge-estado';

type FilaSolicitudProps = Readonly<{ solicitud: SolicitudAdmin }>;

/** Longitud a partir de la cual la descripción se trunca y ofrece expandir. */
const LIMITE_DESCRIPCION = 140;

/** Fila de sólo lectura de una solicitud (el cierre aún no existe en la API). */
export function FilaSolicitud({ solicitud }: FilaSolicitudProps) {
  const { nombreContacto, emailContacto, telefono, municipio, descripcion, gremios, estado, creadoEn } =
    solicitud;
  const [expandida, setExpandida] = useState(false);
  const larga = descripcion.trim().length > LIMITE_DESCRIPCION;
  const visible = !larga || expandida ? descripcion : `${descripcion.slice(0, LIMITE_DESCRIPCION)}…`;
  const idDescripcion = `solicitud-${solicitud.id}-descripcion`;

  return (
    <li className="rounded-lg border border-borde bg-superficie p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="font-display text-lg text-texto">{nombreContacto}</h2>
          <p className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-sm text-texto-suave">
            <a
              href={`mailto:${emailContacto}`}
              className="underline underline-offset-4 outline-none hover:text-acento-hover focus:ring-2 focus:ring-acento-nogal"
            >
              {emailContacto}
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
            <time dateTime={creadoEn}>{formatoFecha(creadoEn)}</time>
          </p>
        </div>
        <BadgeEstado estado={estado} />
      </div>

      <div className="mt-3">
        <ChipsGremios gremios={gremios} />
      </div>

      <div className="mt-3">
        <h3 className="text-sm font-medium text-texto">Descripción del trabajo</h3>
        <p id={idDescripcion} className="mt-1 text-sm text-texto-suave">
          {visible}
        </p>
        {larga ? (
          <button
            type="button"
            aria-expanded={expandida}
            aria-controls={idDescripcion}
            onClick={() => setExpandida((anterior) => !anterior)}
            className="mt-2 rounded-md text-sm text-acento-nogal underline underline-offset-4 transition-colors outline-none hover:text-acento-hover focus:ring-2 focus:ring-acento-nogal"
          >
            {expandida ? 'Ver menos' : 'Ver descripción completa'}
          </button>
        ) : null}
      </div>
    </li>
  );
}
