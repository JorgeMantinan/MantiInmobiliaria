'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import { Boton } from '@/components/ui/boton';
import {
  construirQueryInmuebles,
  HABITACIONES_MAX,
  leerFiltros,
  rangoPrecioInconsistente,
} from '../filtros';
import { ETIQUETAS_ESTADO, ETIQUETAS_TIPO } from '../formato';
import type { FiltrosInmuebles } from '../types';
import { CampoFiltro, CLASE_CAMPO } from './campo-filtro';
import { CamposPrecio } from './campos-precio';

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

        <CamposPrecio
          precioMin={filtros.precioMin}
          precioMax={filtros.precioMax}
          avisoRango={avisoRango}
        />

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

      <div className="mt-4 flex flex-wrap justify-end gap-2">
        <Boton variante="secundario" onClick={onLimpiar}>
          Limpiar filtros
        </Boton>
        <Boton type="submit">Aplicar filtros</Boton>
      </div>
    </form>
  );
}
