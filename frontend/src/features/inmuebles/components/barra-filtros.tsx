'use client';

import type { FormEvent, ReactNode } from 'react';
import { Boton } from '@/components/ui/boton';
import { leerFiltros } from '../filtros';
import { ETIQUETAS_ESTADO, ETIQUETAS_TIPO } from '../formato';
import type { FiltrosInmuebles } from '../types';

const CLASE_CAMPO =
  'rounded-md border border-borde bg-superficie px-3 py-2 text-sm text-texto outline-none transition-colors focus:ring-2 focus:ring-acento-nogal';
const CLASE_ETIQUETA = 'block text-sm font-medium text-texto';

type CampoFiltroProps = Readonly<{
  id: string;
  etiqueta: string;
  children: ReactNode;
}>;

/** Contenedor de un campo con su label accesible (los inputs son no controlados). */
function CampoFiltro({ id, etiqueta, children }: CampoFiltroProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className={CLASE_ETIQUETA}>
        {etiqueta}
      </label>
      {children}
    </div>
  );
}

type BarraFiltrosProps = Readonly<{
  /** Valores con los que arranca el formulario (los de la URL). */
  filtros: FiltrosInmuebles;
  onAplicar: (filtros: FiltrosInmuebles) => void;
  onLimpiar: () => void;
}>;

/**
 * Filtros del listado. Al enviar se leen del `FormData`, se validan con
 * `leerFiltros` y el contenedor sincroniza la URL (`router.replace`).
 */
export function BarraFiltros({ filtros, onAplicar, onLimpiar }: BarraFiltrosProps) {
  function aplicar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const params = new URLSearchParams();
    for (const [clave, valor] of new FormData(evento.currentTarget)) {
      if (typeof valor === 'string') params.set(clave, valor);
    }
    onAplicar(leerFiltros(params));
  }

  return (
    <form
      aria-label="Filtros de búsqueda"
      onSubmit={aplicar}
      className="rounded-lg border border-borde bg-superficie p-5"
    >
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <CampoFiltro id="filtro-estado" etiqueta="Estado">
          <select
            id="filtro-estado"
            name="estado"
            defaultValue={filtros.estado ?? ''}
            className={CLASE_CAMPO}
          >
            <option value="">Cualquiera</option>
            <option value="VENTA">{ETIQUETAS_ESTADO.VENTA}</option>
            <option value="ALQUILER">{ETIQUETAS_ESTADO.ALQUILER}</option>
          </select>
        </CampoFiltro>

        <CampoFiltro id="filtro-tipo" etiqueta="Tipo">
          <select
            id="filtro-tipo"
            name="tipo"
            defaultValue={filtros.tipo ?? ''}
            className={CLASE_CAMPO}
          >
            <option value="">Cualquiera</option>
            {Object.entries(ETIQUETAS_TIPO).map(([valor, etiqueta]) => (
              <option key={valor} value={valor}>
                {etiqueta}
              </option>
            ))}
          </select>
        </CampoFiltro>

        <CampoFiltro id="filtro-municipio" etiqueta="Municipio">
          <input
            id="filtro-municipio"
            name="municipio"
            type="search"
            maxLength={120}
            defaultValue={filtros.municipio ?? ''}
            placeholder="Madrid…"
            className={CLASE_CAMPO}
          />
        </CampoFiltro>

        <CampoFiltro id="filtro-orden" etiqueta="Ordenar por">
          <select
            id="filtro-orden"
            name="sort"
            defaultValue={filtros.sort ?? ''}
            className={CLASE_CAMPO}
          >
            <option value="">Más recientes</option>
            <option value="precio_asc">Precio: de menor a mayor</option>
            <option value="precio_desc">Precio: de mayor a menor</option>
          </select>
        </CampoFiltro>

        <CampoFiltro id="filtro-precio-min" etiqueta="Precio mínimo (€)">
          <input
            id="filtro-precio-min"
            name="precioMin"
            type="number"
            min={0}
            inputMode="numeric"
            defaultValue={filtros.precioMin ?? ''}
            className={CLASE_CAMPO}
          />
        </CampoFiltro>

        <CampoFiltro id="filtro-precio-max" etiqueta="Precio máximo (€)">
          <input
            id="filtro-precio-max"
            name="precioMax"
            type="number"
            min={0}
            inputMode="numeric"
            defaultValue={filtros.precioMax ?? ''}
            className={CLASE_CAMPO}
          />
        </CampoFiltro>

        <CampoFiltro id="filtro-habitaciones" etiqueta="Habitaciones mínimas">
          <input
            id="filtro-habitaciones"
            name="habitacionesMin"
            type="number"
            min={0}
            inputMode="numeric"
            defaultValue={filtros.habitacionesMin ?? ''}
            className={CLASE_CAMPO}
          />
        </CampoFiltro>
      </div>

      <div className="mt-4 flex flex-wrap justify-end gap-2">
        <Boton variante="secundario" onClick={onLimpiar}>
          Limpiar filtros
        </Boton>
        <Boton type="submit">Aplicar filtros</Boton>
      </div>
    </form>
  );
}
