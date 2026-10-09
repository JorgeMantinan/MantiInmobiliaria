'use client';

import type { FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { Boton } from '@/components/ui/boton';
import { ETIQUETAS_ESTADO } from '../formato';

const CLASE_CAMPO =
  'w-full rounded-md border border-borde bg-superficie px-3 py-2 text-sm text-texto outline-none transition-colors focus:ring-2 focus:ring-acento-nogal';
const CLASE_ETIQUETA = 'block text-sm font-medium text-texto';

/** Búsqueda rápida del home: sólo municipio y estado → `/inmuebles?…`. */
export function BuscadorRapido() {
  const router = useRouter();

  function buscar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const params = new URLSearchParams();
    for (const [clave, valor] of new FormData(evento.currentTarget)) {
      if (typeof valor === 'string' && valor.trim() !== '') params.set(clave, valor.trim());
    }
    const query = params.toString();
    router.push(`/inmuebles${query === '' ? '' : `?${query}`}`);
  }

  return (
    <form
      aria-label="Búsqueda rápida"
      onSubmit={buscar}
      className="mt-8 flex max-w-3xl flex-col gap-4 rounded-lg border border-borde bg-fondo p-5 sm:flex-row sm:items-end"
    >
      <div className="flex-1">
        <label htmlFor="buscador-municipio" className={CLASE_ETIQUETA}>
          Municipio
        </label>
        <input
          id="buscador-municipio"
          name="municipio"
          type="search"
          maxLength={120}
          placeholder="Madrid…"
          className={`${CLASE_CAMPO} mt-1.5`}
        />
      </div>
      <div className="sm:w-52">
        <label htmlFor="buscador-estado" className={CLASE_ETIQUETA}>
          Estado
        </label>
        <select id="buscador-estado" name="estado" className={`${CLASE_CAMPO} mt-1.5`}>
          <option value="">Cualquiera</option>
          <option value="VENTA">{ETIQUETAS_ESTADO.VENTA}</option>
          <option value="ALQUILER">{ETIQUETAS_ESTADO.ALQUILER}</option>
        </select>
      </div>
      <Boton type="submit">Buscar</Boton>
    </form>
  );
}
