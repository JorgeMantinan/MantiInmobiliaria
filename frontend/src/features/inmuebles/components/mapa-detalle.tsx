'use client';

import 'leaflet/dist/leaflet.css';
// El plugin necesita su CSS (define las clases de las que lee tamaño/ancla/URL
// del icono por defecto) y su JS en ese orden, después de Leaflet.
import 'leaflet-defaulticon-compatibility/dist/leaflet-defaulticon-compatibility.css';
import 'leaflet-defaulticon-compatibility';
import { MapContainer, Marker, Popup, TileLayer } from 'react-leaflet';

const CLAVE_CARTO = process.env.NEXT_PUBLIC_CARTO_KEY ?? '';
const ATRIBUCION = '© OpenStreetMap contributors, © CARTO';

type MapaDetalleProps = Readonly<{
  lat: number;
  lng: number;
  titulo: string;
}>;

/**
 * Mapa interactivo de la ficha (sólo cliente: Leaflet necesita `window`).
 * `scrollWheelZoom` desactivado para no secuestrar la rueda del ratón al
 * desplazarse por la página.
 */
export function MapaDetalle({ lat, lng, titulo }: MapaDetalleProps) {
  return (
    <div
      role="region"
      aria-label={`Mapa interactivo de la ubicación de ${titulo}`}
      className="h-72 w-full overflow-hidden rounded-md border border-borde bg-superficie-alta"
    >
      <MapContainer
        center={[lat, lng]}
        zoom={16}
        scrollWheelZoom={false}
        zoomControl
        className="h-full w-full"
      >
        <TileLayer
          url={`https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png?key=${CLAVE_CARTO}`}
          attribution={ATRIBUCION}
        />
        <Marker position={[lat, lng]} alt={`Ubicación de ${titulo}`}>
          <Popup>{titulo}</Popup>
        </Marker>
      </MapContainer>
    </div>
  );
}
