'use client';

import 'leaflet/dist/leaflet.css';
import Link from 'next/link';
import L from 'leaflet';
import { useEffect, useMemo } from 'react';
import { MapContainer, Marker, Popup, TileLayer, useMap } from 'react-leaflet';
import { formatoPrecio } from '../formato';
import { ATRIBUCION, PLANTILLA_TESELA } from '../teselas';
import type { InmuebleResumen } from '../types';

/** Zoom de arranque: `fitBounds` lo ajusta en cuanto hay marcadores. */
const ZOOM_INICIAL = 13;
/** Centro por defecto (Madrid) hasta que haya posiciones que encuadrar. */
const CENTRO_INICIAL: [number, number] = [40.4168, -3.7038];
/** Margen en píxeles que `fitBounds` deja alrededor del conjunto de pines. */
export const MARGEN_BOUNDS = 40;
/** Zoom máximo al encuadrar: un único inmueble no debe acabar en callejón. */
export const ZOOM_MAXIMO_BOUNDS = 16;

type MapaListadoProps = Readonly<{
  inmuebles: readonly InmuebleResumen[];
}>;

/** Par `[lat, lng]` en el formato que acepta Leaflet (`LatLngTuple`). */
type Posicion = [number, number];

/**
 * Pin del listado (divIcon de Leaflet): círculo nogal con borde crema, con
 * los tokens del sistema madera (`bg-acento-nogal`, `border-superficie`) en
 * vez del pin azul genérico de Leaflet.
 */
function iconoMarcador(): L.DivIcon {
  return L.divIcon({
    className: '',
    html: '<span class="block h-6 w-6 rounded-full border-[3px] border-superficie bg-acento-nogal shadow-sm"></span>',
    iconSize: [24, 24],
    iconAnchor: [12, 12],
    popupAnchor: [0, -14],
  });
}

/**
 * Encuadra el mapa a TODOS los marcadores cargados (primera página y
 * acumulados de "Cargar más") con un margen; vuelve a hacerlo cuando cambian
 * las posiciones (nuevos filtros o página siguiente).
 */
function AjusteBounds({ posiciones }: Readonly<{ posiciones: readonly Posicion[] }>) {
  const map = useMap();
  useEffect(() => {
    if (posiciones.length === 0) return;
    map.fitBounds(L.latLngBounds([...posiciones]), {
      padding: [MARGEN_BOUNDS, MARGEN_BOUNDS],
      maxZoom: ZOOM_MAXIMO_BOUNDS,
    });
  }, [map, posiciones]);
  return null;
}

/**
 * Mapa Leaflet del listado: un marcador por inmueble CARGADO (los que ya están
 * en el hook, sin fetch propio) con popup a su ficha. Sólo cliente: Leaflet
 * necesita `window`; el wrapper lo carga con `ssr: false`.
 */
export function MapaListado({ inmuebles }: MapaListadoProps) {
  const posiciones = useMemo(
    () => inmuebles.map((inmueble): Posicion => [inmueble.lat, inmueble.lng]),
    [inmuebles],
  );
  const icono = useMemo(() => iconoMarcador(), []);
  const centro = posiciones[0] ?? CENTRO_INICIAL;

  return (
    <div
      role="region"
      aria-label="Mapa con la ubicación de los inmuebles listados"
      className="h-[600px] w-full overflow-hidden rounded-md border border-borde bg-superficie-alta"
    >
      <MapContainer
        center={centro}
        zoom={ZOOM_INICIAL}
        scrollWheelZoom={false}
        zoomControl
        className="h-full w-full"
      >
        <TileLayer url={PLANTILLA_TESELA} attribution={ATRIBUCION} />
        <AjusteBounds posiciones={posiciones} />
        {inmuebles.map((inmueble) => (
          <Marker
            key={inmueble.id}
            position={[inmueble.lat, inmueble.lng]}
            icon={icono}
            alt={`Ubicación de ${inmueble.titulo}`}
          >
            <Popup>
              <div className="min-w-44">
                <p className="font-medium text-texto">{inmueble.titulo}</p>
                <p className="text-acento-nogal">{formatoPrecio(inmueble.precio)}</p>
                <Link
                  href={`/inmuebles/${inmueble.id}`}
                  className="mt-1 inline-block text-sm text-acento-nogal underline"
                >
                  Ver ficha
                </Link>
              </div>
            </Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  );
}
