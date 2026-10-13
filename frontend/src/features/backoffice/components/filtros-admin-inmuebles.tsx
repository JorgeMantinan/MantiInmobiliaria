'use client';

import type { FormEvent } from 'react';
import { Boton } from '@/components/ui/boton';
import { CLASE_CAMPO } from '@/features/inmuebles/components/campo-filtro';
import type { FiltrosAdmin } from '../hooks/use-inmuebles-admin';

const CLASE_PESTANA =
  'rounded-md border px-3 py-1.5 text-sm transition-colors outline-none focus:ring-2 focus:ring-acento-nogal';

type OpcionEstado = Readonly<{ valor: FiltrosAdmin['estado']; etiqueta: string }>;

const PESTANAS: readonly OpcionEstado[] = [
  { valor: undefined, etiqueta: 'Todos' },
  { valor: 'VENTA', etiqueta: 'En venta' },
  { valor: 'ALQUILER', etiqueta: 'En alquiler' },
];

type PestanasEstadoProps = Readonly<{
  /** Estado vigente de la URL (`null`/valor no admitido ⇒ pestaña "Todos"). */
  estado: string | null;
  /** Aplica el nuevo estado CONSERVANDO el resto de filtros vigentes. */
  onEstado: (estado: FiltrosAdmin['estado']) => void;
  /** true ⇒ botones deshabilitados (un borrado en vuelo). */
  disabled?: boolean;
}>;

/** Pestañas Todos / En venta / En alquiler del listado admin. */
export function PestanasEstadoInmuebles({ estado, onEstado, disabled = false }: PestanasEstadoProps) {
  return (
    <div role="group" aria-label="Filtrar por estado" className="mt-6 flex flex-wrap gap-2">
      {PESTANAS.map((opcion) => {
        const activa = opcion.valor === undefined ? estado === null : estado === opcion.valor;
        return (
          <button
            key={opcion.etiqueta}
            type="button"
            aria-pressed={activa}
            disabled={disabled}
            onClick={() => onEstado(opcion.valor)}
            className={`${CLASE_PESTANA} ${
              activa
                ? 'border-acento-nogal bg-superficie-alta font-medium text-texto'
                : 'border-borde bg-superficie text-texto-suave hover:bg-superficie-alta'
            } ${disabled ? 'cursor-not-allowed opacity-60' : ''}`}
          >
            {opcion.etiqueta}
          </button>
        );
      })}
    </div>
  );
}

type FiltrosAdminInmueblesProps = Readonly<{
  /** Filtros vigentes (URL): valores iniciales del form y merge al enviar. */
  filtros: FiltrosAdmin;
  onAplicar: (filtros: FiltrosAdmin) => void;
  onLimpiar: () => void;
  /** true ⇒ botones deshabilitados (un borrado en vuelo). */
  disabled?: boolean;
}>;

/**
 * Buscador de municipio + filtro de destacados del backoffice. Presentacional
 * puro: los valores salen de los filtros vigentes y el submit hace merge con
 * el estado actual (que el form no edita) antes de navegar.
 */
export function FiltrosAdminInmuebles({
  filtros,
  onAplicar,
  onLimpiar,
  disabled = false,
}: FiltrosAdminInmueblesProps) {
  function enviar(evento: FormEvent<HTMLFormElement>): void {
    evento.preventDefault();
    const datos = new FormData(evento.currentTarget);
    const municipio = String(datos.get('municipio') ?? '').trim().slice(0, 120);
    const destacado = datos.get('destacado') === 'on';
    onAplicar({
      estado: filtros.estado,
      municipio: municipio === '' ? undefined : municipio,
      destacado: destacado ? true : undefined,
    });
  }

  return (
    <form
      aria-label="Buscar por municipio y destacado"
      onSubmit={enviar}
      className="mt-4 flex flex-wrap items-end gap-3 rounded-lg border border-borde bg-superficie p-4"
    >
      <div className="flex min-w-40 flex-col gap-1.5">
        <label htmlFor="admin-municipio" className="text-sm font-medium text-texto">
          Municipio
        </label>
        <input
          // `key` con el valor de la URL: al navegar (limpiar, pestañas…) el
          // input no controlado se remonta y refleja el estado real.
          key={filtros.municipio ?? ''}
          id="admin-municipio"
          name="municipio"
          type="search"
          maxLength={120}
          defaultValue={filtros.municipio ?? ''}
          placeholder="Madrid…"
          className={CLASE_CAMPO}
        />
      </div>
      <div className="flex items-center gap-2 pb-2">
        <input
          id="admin-destacado"
          name="destacado"
          type="checkbox"
          defaultChecked={filtros.destacado === true}
          className="size-4 accent-acento-nogal"
        />
        <label htmlFor="admin-destacado" className="text-sm font-medium text-texto">
          Sólo destacados
        </label>
      </div>
      <div className="flex gap-2">
        <Boton type="submit" disabled={disabled}>
          Buscar
        </Boton>
        <Boton variante="secundario" disabled={disabled} onClick={onLimpiar}>
          Limpiar filtros
        </Boton>
      </div>
    </form>
  );
}
