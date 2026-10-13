/**
 * Serialización de filtros entre el listado (`FiltrosInmuebles`, texto desde
 * la URL) y las alertas (`FiltrosAlerta`, números en el body de la API).
 *
 * Un único punto de verdad para las dos conversiones, así el botón "Guardar
 * como alerta", el enlace "Ver búsqueda" de cada alerta y el backend guardan
 * exactamente lo mismo (issue #96).
 */
import { ETIQUETAS_ESTADO, ETIQUETAS_TIPO, formatoPrecio } from '@/features/inmuebles/formato';
import type { FiltrosInmuebles } from '@/features/inmuebles/types';
import type { FiltrosAlerta } from './types';

/** Texto entero → número; vacío o no numérico se descarta (igual que la URL). */
function numero(valor: string | undefined): number | undefined {
  if (valor === undefined || valor.trim() === '') return undefined;
  const resultado = Number(valor);
  return Number.isFinite(resultado) ? resultado : undefined;
}

/**
 * Filtros del listado → payload de la API.
 *
 * `sort` NO viaja: el orden no es filtro (el backend tampoco lo guarda), y los
 * textos numéricos pasan a número para que el JSON cumpla el contrato
 * `AlertaFiltros` del backend.
 */
export function filtrosParaAlerta(filtros: FiltrosInmuebles): FiltrosAlerta {
  const alerta: {
    estado?: FiltrosAlerta['estado'];
    tipo?: FiltrosAlerta['tipo'];
    municipio?: string;
    precioMin?: number;
    precioMax?: number;
    habitacionesMin?: number;
    destacado?: boolean;
  } = {};

  if (filtros.estado) alerta.estado = filtros.estado;
  if (filtros.tipo) alerta.tipo = filtros.tipo;
  if (filtros.municipio) alerta.municipio = filtros.municipio;
  const precioMin = numero(filtros.precioMin);
  const precioMax = numero(filtros.precioMax);
  const habitacionesMin = numero(filtros.habitacionesMin);
  if (precioMin !== undefined) alerta.precioMin = precioMin;
  if (precioMax !== undefined) alerta.precioMax = precioMax;
  if (habitacionesMin !== undefined) alerta.habitacionesMin = habitacionesMin;
  if (filtros.destacado !== undefined) alerta.destacado = filtros.destacado;

  return alerta;
}

/** Filtros de una alerta → filtros del listado (texto), para su enlace. */
export function filtrosDesdeAlerta(filtros: FiltrosAlerta): FiltrosInmuebles {
  const listado: {
    estado?: FiltrosInmuebles['estado'];
    tipo?: FiltrosInmuebles['tipo'];
    municipio?: string;
    precioMin?: string;
    precioMax?: string;
    habitacionesMin?: string;
    destacado?: boolean;
  } = {};

  if (filtros.estado) listado.estado = filtros.estado;
  if (filtros.tipo) listado.tipo = filtros.tipo;
  if (filtros.municipio) listado.municipio = filtros.municipio;
  if (filtros.precioMin !== undefined) listado.precioMin = String(filtros.precioMin);
  if (filtros.precioMax !== undefined) listado.precioMax = String(filtros.precioMax);
  if (filtros.habitacionesMin !== undefined) {
    listado.habitacionesMin = String(filtros.habitacionesMin);
  }
  if (filtros.destacado !== undefined) listado.destacado = filtros.destacado;

  return listado;
}

/**
 * Resumen legible de los filtros para la tarjeta de "Mis alertas"
 * (`En venta · Piso · Madrid · 100.000 € – 250.000 €`).
 */
export function resumenFiltros(filtros: FiltrosAlerta): string {
  const partes: string[] = [];

  if (filtros.estado) partes.push(ETIQUETAS_ESTADO[filtros.estado]);
  if (filtros.tipo) partes.push(ETIQUETAS_TIPO[filtros.tipo]);
  if (filtros.municipio) partes.push(filtros.municipio);

  if (filtros.precioMin !== undefined && filtros.precioMax !== undefined) {
    partes.push(`${formatoPrecio(filtros.precioMin)} – ${formatoPrecio(filtros.precioMax)}`);
  } else if (filtros.precioMin !== undefined) {
    partes.push(`Desde ${formatoPrecio(filtros.precioMin)}`);
  } else if (filtros.precioMax !== undefined) {
    partes.push(`Hasta ${formatoPrecio(filtros.precioMax)}`);
  }

  if (filtros.habitacionesMin !== undefined) {
    partes.push(`${filtros.habitacionesMin}+ habitaciones`);
  }
  if (filtros.destacado === true) partes.push('Sólo destacados');
  if (filtros.destacado === false) partes.push('Sin destacados');

  return partes.length === 0 ? 'Todos los inmuebles' : partes.join(' · ');
}
