'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { obtenerPreciosZona } from '../api';
import type { PreciosZona } from '../types';

/** Reserva por si la API no devolviera la atribución. */
const FUENTE_POR_DEFECTO = 'Ministerio de Vivienda y Agenda Urbana';

const FORMATEADOR = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 });

type PrecioZonaProps = Readonly<{
  /** Código INE del municipio; sin él no se consulta ni se pinta nada. */
  codigoIne: string | null;
  /** Nombre del municipio, para la frase de alquiler. */
  municipio: string;
}>;

/**
 * Tarjeta "Precio medio en la zona" con los datos oficiales del MIVAU.
 *
 * Estados de carga: cargando, error de red y 404 (municipio sin dato
 * publicado) dejan la sección OCULTA — es información complementaria y un
 * fallo no debe romper la ficha. Sólo se pinta cuando la API devuelve al
 * menos un bloque con el indicador que se muestra.
 */
export function PrecioZona({ codigoIne, municipio }: PrecioZonaProps) {
  const [estado, setEstado] = useState<{
    codigoIne: string | null;
    precios: PreciosZona | null;
  }>({ codigoIne: null, precios: null });

  useEffect(() => {
    if (!codigoIne) {
      return;
    }
    let vigente = true;
    obtenerPreciosZona(codigoIne)
      .then((datos) => {
        if (vigente) setEstado({ codigoIne, precios: datos });
      })
      .catch(() => {
        if (vigente) setEstado({ codigoIne, precios: null });
      });
    return () => {
      vigente = false;
    };
  }, [codigoIne]);

  // El resultado sólo se muestra si corresponde al municipio ACTUAL: al cambiar
  // de ficha nunca se cuelan los precios del inmueble anterior.
  const precios = estado.codigoIne === codigoIne ? estado.precios : null;

  if (!codigoIne || !precios) {
    return null;
  }

  const lineas = construirLineas(precios, municipio);
  if (lineas.length === 0) {
    return null;
  }

  const fuente = precios.fuente?.trim() ? precios.fuente : FUENTE_POR_DEFECTO;

  return (
    <section
      aria-labelledby="titulo-precio-zona"
      className="mt-6 rounded-lg border border-borde bg-superficie p-5"
    >
      <h2 id="titulo-precio-zona" className="font-display text-xl text-texto">
        Precio medio en la zona
      </h2>
      <ul className="mt-3 space-y-1.5">{lineas}</ul>
      <p className="mt-4 text-xs text-texto-suave">
        Fuente: {fuente} (SERPAVI / Valor tasado)
      </p>
    </section>
  );
}

function construirLineas(precios: PreciosZona, municipio: string): ReactNode[] {
  const lineas: ReactNode[] = [];
  const renta = precios.alquiler?.rentaMedianaEurM2Mes;
  const cuantia = precios.alquiler?.cuantiaMedianaEurMes;
  const valor = precios.venta?.valorEurM2;

  if (renta !== null && renta !== undefined) {
    lineas.push(
      <li key="alquiler" className="text-texto">
        Alquiler medio en <span className="font-medium">{municipio}</span>:{' '}
        <span className="font-semibold text-acento-nogal">
          {FORMATEADOR.format(renta)} €/m²/mes
        </span>{' '}
        <span className="text-texto-suave">(dato municipal)</span>
      </li>,
    );
  } else if (cuantia !== null && cuantia !== undefined) {
    // Sin renta derivada (p. ej. el municipio no publica superficie) se muestra
    // la cuantía mensual del contrato, que sí viene publicada.
    lineas.push(
      <li key="alquiler-cuantia" className="text-texto">
        Alquiler medio:{' '}
        <span className="font-semibold text-acento-nogal">
          {FORMATEADOR.format(cuantia)} €/mes
        </span>{' '}
        <span className="text-texto-suave">(dato municipal)</span>
      </li>,
    );
  }

  if (precios.venta && valor !== null && valor !== undefined) {
    lineas.push(
      <li key="venta" className="text-texto">
        Valor tasado de venta en <span className="font-medium">{precios.venta.provincia}</span>:{' '}
        <span className="font-semibold text-acento-nogal">
          {FORMATEADOR.format(valor)} €/m²
        </span>{' '}
        <span className="text-texto-suave">(dato provincial)</span>
      </li>,
    );
  }

  return lineas;
}
