'use client';

import 'leaflet/dist/leaflet.css';
// El plugin necesita su CSS (define las clases de las que lee tamaño/ancla/URL
// del icono por defecto) y su JS en ese orden, después de Leaflet.
import 'leaflet-defaulticon-compatibility/dist/leaflet-defaulticon-compatibility.css';
import 'leaflet-defaulticon-compatibility';
import { useEffect } from 'react';
import { MapContainer, Marker, Popup, TileLayer } from 'react-leaflet';
import { ATRIBUCION, avisarClaveCartoAusente, PLANTILLA_TESELA_CARTO } from '../carto';

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
  // El aviso de clave ausente se emite en un efecto, nunca durante el render.
  useEffect(() => {
    avisarClaveCartoAusente();
  }, []);

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
        <TileLayer url={PLANTILLA_TESELA_CARTO} attribution={ATRIBUCION} />
        <Marker position={[lat, lng]} alt={`Ubicación de ${titulo}`}>
          <Popup>{titulo}</Popup>
        </Marker>
      </MapContainer>
    </div>
  );
}
