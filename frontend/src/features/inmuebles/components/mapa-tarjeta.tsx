import { latLngATile, TAMANO_TESELA } from "@/lib/geo";
import { ATRIBUCION, avisarClaveCartoAusente, urlTeselaCarto } from "../carto";

/** Zoom del tile estático de la tarjeta: nivel de calle, sin cargar detalle de más. */
const ZOOM_TESELA = 16;
/**
 * La caja del mini-mapa es 2,5 veces más ancha que alta (`aspect-[5/2]`) y el
 * tile se dibuja al 200 % de ese ancho: así el tile SIEMPRE cubre la caja
 * (aunque el punto esté pegado al borde de la tesela) y el pin queda dentro.
 */
const RELACION_CAJA = 2.5;
const IMAGEN_ANCHO_PCT = 200;
const IMAGEN_ALTO_PCT = IMAGEN_ANCHO_PCT * RELACION_CAJA;

type MapaTarjetaProps = Readonly<{
  lat: number;
  lng: number;
  titulo: string;
  /** Dirección en texto; también se muestra debajo del mapa. */
  direccion: string;
  municipio: string;
}>;

type Posicion = Readonly<{ inicio: number; punto: number }>;

/**
 * Ubica el tile (`tamanoPct` % de la caja) para que contenga el punto y devuelva
 * el porcentaje del punto dentro de la caja. `inicio` es la posición del tile
 * y `punto` la del pin, ambos en coordenadas de la caja (0..100).
 *
 * Exportada sólo para testearla (`mapa-tarjeta.test.tsx`).
 */
export function ubicar(offset: number, tamanoPct: number): Posicion {
  const dentro = (offset / TAMANO_TESELA) * tamanoPct;
  const inicio = Math.min(Math.max(50 - dentro, 100 - tamanoPct), 0);
  return { inicio, punto: inicio + dentro };
}

/**
 * Mini-mapa ESTÁTICO de la tarjeta: un único tile de CARTO en z16 más un pin
 * SVG posicionado con los offsets de `latLngATile`. Sin JavaScript de mapas,
 * por eso es seguro en server components.
 */
export function MapaTarjeta({ lat, lng, titulo, direccion, municipio }: MapaTarjetaProps) {
  avisarClaveCartoAusente();
  const { x, y, offsetX, offsetY } = latLngATile(lat, lng, ZOOM_TESELA);
  const horizontal = ubicar(offsetX, IMAGEN_ANCHO_PCT);
  const vertical = ubicar(offsetY, IMAGEN_ALTO_PCT);
  const alt = `Mapa de la ubicación de ${titulo} en ${direccion}, ${municipio}`;

  return (
    <div className="overflow-hidden rounded-md border border-borde bg-superficie">
      <div className="relative aspect-[5/2] w-full overflow-hidden bg-superficie-alta">
        {/* Tile suelto de CARTO con offsets en %: no es una imagen de contenido
            para `next/image`, que además necesitaría `remotePatterns`. */}
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={urlTeselaCarto(ZOOM_TESELA, x, y)}
          alt={alt}
          width={TAMANO_TESELA}
          height={TAMANO_TESELA}
          loading="lazy"
          decoding="async"
          className="absolute aspect-square w-[200%] max-w-none object-cover"
          style={{ left: `${horizontal.inicio}%`, top: `${vertical.inicio}%` }}
        />
        <svg
          aria-hidden="true"
          focusable="false"
          viewBox="0 0 24 32"
          className="pointer-events-none absolute h-7 w-6 -translate-x-1/2 -translate-y-full fill-acento-nogal"
          style={{ left: `${horizontal.punto}%`, top: `${vertical.punto}%` }}
        >
          <path d="M12 0.5C6.2 0.5 1.5 5.2 1.5 11c0 7.6 8.4 16.4 10 17.9a1.5 1.5 0 0 0 2 0c1.6-1.5 10-10.3 10-17.9 0-5.8-4.7-10.5-10.5-10.5Z" />
          <circle cx="12" cy="11" r="4" className="fill-superficie" />
        </svg>
      </div>
      <p className="border-t border-borde px-3 py-1 text-[11px] text-texto-suave">{ATRIBUCION}</p>
    </div>
  );
}
