'use client';

import { useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { Boton } from '@/components/ui/boton';
import {
  construirQueryInmuebles,
  HABITACIONES_MAX,
  leerFiltros,
  PRECIO_MAXIMO_CONSULTA,
  rangoPrecioInconsistente,
} from '../filtros';
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
  const [avisoRango, setAvisoRango] = useState<string | null>(null);

  function aplicar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    // Se parte de los filtros vigentes (p. ej. `?destacado=true`, que no tiene
    // campo en el formulario) y sólo se pisan los que trae el formulario; los
    // campos vacíos se eliminan para que sirvan de "quitar este filtro".
    const params = new URLSearchParams(construirQueryInmuebles(filtros).replace(/^\?/, ''));
    for (const [clave, valor] of new FormData(evento.currentTarget)) {
      if (typeof valor !== 'string') continue;
      if (valor.trim() === '') params.delete(clave);
      else params.set(clave, valor.trim());
    }

    if (rangoPrecioInconsistente(params)) {
      // Se avisa y NO se navega: la query resultante la rechazaría el backend.
      setAvisoRango('El precio mínimo no puede superar al máximo.');
      return;
    }
    setAvisoRango(null);
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
            max={PRECIO_MAXIMO_CONSULTA}
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
            max={PRECIO_MAXIMO_CONSULTA}
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
            max={HABITACIONES_MAX}
            inputMode="numeric"
            defaultValue={filtros.habitacionesMin ?? ''}
            className={CLASE_CAMPO}
          />
        </CampoFiltro>
      </div>

      {avisoRango !== null ? (
        <p role="status" className="mt-3 text-sm text-acento-hover">
          {avisoRango}
        </p>
      ) : null}

      <div className="mt-4 flex flex-wrap justify-end gap-2">
        <Boton variante="secundario" onClick={onLimpiar}>
          Limpiar filtros
        </Boton>
        <Boton type="submit">Aplicar filtros</Boton>
      </div>
    </form>
  );
}
