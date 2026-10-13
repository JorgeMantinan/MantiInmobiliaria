'use client';

import Link from 'next/link';
import { Boton } from '@/components/ui/boton';
import { queryListado } from '@/features/inmuebles/filtros';
import { ENLACE_SECUNDARIO } from '@/features/swipes/clases';
import { filtrosDesdeAlerta, resumenFiltros } from '../serializacion';
import type { AlertaBusqueda } from '../types';

type TarjetaAlertaProps = Readonly<{
  alerta: AlertaBusqueda;
  /** true mientras su PATCH/DELETE sigue en vuelo (deshabilita sus botones). */
  ocupada: boolean;
  onAlternar: (alerta: AlertaBusqueda) => void;
  onEliminar: (id: number) => void;
}>;

/** Fecha ISO-8601 → `13 oct 2026, 17:05` (hora local del navegador). */
function formatoFecha(iso: string): string {
  return new Date(iso).toLocaleString('es-ES', { dateStyle: 'medium', timeStyle: 'short' });
}

/**
 * Tarjeta de una alerta en "Mis alertas": estado, resumen de filtros, último
 * aviso y las tres acciones (ver búsqueda, pausar/activar, eliminar).
 */
export function TarjetaAlerta({ alerta, ocupada, onAlternar, onEliminar }: TarjetaAlertaProps) {
  return (
    <article className="flex h-full flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <h2 className="font-display text-lg text-texto">{alerta.nombre}</h2>
        <span
          className={`shrink-0 rounded-md border border-borde px-2 py-0.5 text-xs ${
            alerta.activa ? 'bg-superficie-alta text-texto' : 'bg-superficie text-texto-suave'
          }`}
        >
          {alerta.activa ? 'Activa' : 'En pausa'}
        </span>
      </div>

      <p className="text-sm text-texto-suave">{resumenFiltros(alerta.filtros)}</p>

      <p className="text-xs text-texto-suave">
        {alerta.ultimoAvisoEn !== null
          ? `Último aviso: ${formatoFecha(alerta.ultimoAvisoEn)}`
          : 'Aún no has recibido avisos'}
      </p>

      <div className="mt-auto flex flex-wrap gap-2 pt-2">
        <Link
          href={`/inmuebles${queryListado(filtrosDesdeAlerta(alerta.filtros), 'lista')}`}
          className={ENLACE_SECUNDARIO}
        >
          Ver búsqueda
        </Link>
        <Boton
          variante="secundario"
          onClick={() => onAlternar(alerta)}
          disabled={ocupada}
          aria-label={`${alerta.activa ? 'Pausar' : 'Activar'} alerta: ${alerta.nombre}`}
        >
          {ocupada ? 'Guardando…' : alerta.activa ? 'Pausar' : 'Activar'}
        </Boton>
        <Boton
          variante="secundario"
          onClick={() => onEliminar(alerta.id)}
          disabled={ocupada}
          aria-label={`Eliminar alerta: ${alerta.nombre}`}
        >
          Eliminar
        </Boton>
      </div>
    </article>
  );
}
