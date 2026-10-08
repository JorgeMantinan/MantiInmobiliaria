import type { Metadata } from 'next';
import { BuscadorRapido } from '@/features/inmuebles/components/buscador-rapido';
import { Destacados } from '@/features/inmuebles/components/destacados';
import { BannerProfesionales } from '@/features/profesionales/components/banner-profesionales';

export const metadata: Metadata = {
  title: 'MantiInmobiliaria — Encuentra tu próxima vivienda',
  description:
    'Portal de viviendas en venta y alquiler con fichas detalladas, mapa geolocalizado y filtros por municipio, precio y habitaciones.',
};

const PASOS = [
  {
    titulo: 'Busca con filtros',
    texto: 'Estado, tipo, municipio, precio y habitaciones; los filtros viven en la URL, así que puedes compartirlos.',
  },
  {
    titulo: 'Mira la ubicación',
    texto: 'Cada anuncio muestra su punto en el mapa y la ficha incluye un mapa interactivo de la zona.',
  },
  {
    titulo: 'Decide con calma',
    texto: 'Fotos, superficie, descripción y dirección completa para comparar viviendas sin sorpresas.',
  },
] as const;

/** Home público del portal (sustituye a la plantilla de create-next-app). */
export default function Home() {
  return (
    <>
      <section className="border-b border-borde bg-superficie">
        <div className="mx-auto w-full max-w-6xl px-4 py-16 sm:px-6 sm:py-20">
          <p className="text-xs font-medium uppercase tracking-wide text-texto-suave">
            Portal de viviendas
          </p>
          <h1 className="mt-3 max-w-2xl font-display text-4xl leading-tight text-texto sm:text-5xl">
            Encuentra tu próxima vivienda
          </h1>
          <p className="mt-4 max-w-2xl text-texto-suave">
            Consulta el mapa de cada anuncio, filtra por lo que de verdad te importa y conoce el
            barrio antes de pisar la puerta.
          </p>
          <BuscadorRapido />
        </div>
      </section>

      <Destacados />

      <BannerProfesionales />

      <section aria-labelledby="titulo-como-funciona" className="border-t border-borde bg-fondo">
        <div className="mx-auto w-full max-w-6xl px-4 py-12 sm:px-6">
          <h2 id="titulo-como-funciona" className="font-display text-2xl text-texto">
            Así funciona
          </h2>
          <ul className="mt-6 grid list-none gap-6 sm:grid-cols-3">
            {PASOS.map((paso) => (
              <li key={paso.titulo} className="rounded-lg border border-borde bg-superficie p-5">
                <h3 className="font-display text-lg text-texto">{paso.titulo}</h3>
                <p className="mt-2 text-sm text-texto-suave">{paso.texto}</p>
              </li>
            ))}
          </ul>
        </div>
      </section>
    </>
  );
}
