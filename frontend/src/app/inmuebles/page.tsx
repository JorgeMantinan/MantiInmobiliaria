import type { Metadata } from 'next';
import { Suspense } from 'react';
import { ListadoCargando } from '@/features/inmuebles/components/estados-listado';
import { ListadoInmuebles } from '@/features/inmuebles/components/listado-inmuebles';

export const metadata: Metadata = {
  title: 'Inmuebles en venta y alquiler | MantiInmobiliaria',
  description:
    'Busca pisos, casas, áticos y locales en venta y alquiler con filtros por estado, tipo, municipio, precio y habitaciones; cada anuncio incluye fotos, dirección y mapa.',
  // TODO(Fase 10, cuando el backend tenga dominio propio): metadata e ISR
  // dinámicos por consulta de búsqueda (y de la ficha), con URL canónica.
};

/**
 * Listado público: la página es server (para poder exportar `metadata`) y el
 * listado es cliente porque los filtros se leen de la URL con `useSearchParams`
 * y se escriben con `router.replace`. El `Suspense` es obligatorio para poder
 * prerenderizar el esqueleto de la ruta.
 */
export default function InmueblesPage() {
  return (
    <Suspense
      fallback={
        <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6">
          <ListadoCargando />
        </div>
      }
    >
      <ListadoInmuebles />
    </Suspense>
  );
}
