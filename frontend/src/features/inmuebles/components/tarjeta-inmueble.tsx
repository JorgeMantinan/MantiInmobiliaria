import Link from 'next/link';
import { direccionDe, datosDe, ETIQUETAS_ESTADO, ETIQUETAS_TIPO, formatoPrecio } from '../formato';
import type { InmuebleResumen } from '../types';
import { FotoInmueble } from './foto-inmueble';
import { MapaTarjeta } from './mapa-tarjeta';

type TarjetaInmuebleProps = Readonly<{ inmueble: InmuebleResumen }>;

const ENLACE = 'rounded-md outline-none focus:ring-2 focus:ring-acento-nogal focus:ring-offset-2 focus:ring-offset-superficie';

/** Tarjeta de listado (server-safe): foto, precio, datos, dirección y mini-mapa. */
export function TarjetaInmueble({ inmueble }: TarjetaInmuebleProps) {
  const direccion = direccionDe(inmueble);
  const datos = datosDe(inmueble);
  const href = `/inmuebles/${inmueble.id}`;

  return (
    <article className="flex h-full flex-col overflow-hidden rounded-lg border border-borde bg-superficie transition-colors hover:border-acento-nogal">
      <Link href={href} className={`${ENLACE} block`}>
        <FotoInmueble src={inmueble.imagenUrl} alt={`${inmueble.titulo} en ${direccion}`} />
      </Link>

      <div className="flex flex-1 flex-col gap-3 p-5">
        <div className="flex flex-wrap items-center gap-2 text-xs text-texto-suave">
          <span className="rounded-md border border-borde bg-superficie-alta px-2 py-0.5">
            {ETIQUETAS_ESTADO[inmueble.estado]}
          </span>
          <span className="rounded-md border border-borde px-2 py-0.5">
            {ETIQUETAS_TIPO[inmueble.tipo]}
          </span>
        </div>

        <div>
          <h3 className="font-display text-lg text-texto">
            <Link href={href} className={`${ENLACE} hover:text-acento-hover`}>
              {inmueble.titulo}
            </Link>
          </h3>
          <p className="mt-1 text-xl font-semibold text-acento-nogal">
            {formatoPrecio(inmueble.precio)}
          </p>
          {datos !== '' ? <p className="mt-1 text-sm text-texto-suave">{datos}</p> : null}
          <p className="mt-1 text-sm text-texto">{direccion}</p>
        </div>

        <div className="mt-auto">
          <MapaTarjeta
            lat={inmueble.lat}
            lng={inmueble.lng}
            titulo={inmueble.titulo}
            direccion={direccion}
            municipio={inmueble.municipio}
          />
        </div>
      </div>
    </article>
  );
}
