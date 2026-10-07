'use client';

import { Suspense } from 'react';
import { ListadoCargando } from '@/features/inmuebles/components/estados-listado';
import { ListadoInmuebles } from '@/features/inmuebles/components/listado-inmuebles';

/**
 * Listado público: página cliente porque los filtros se leen de la URL con
 * `useSearchParams` y se escriben con `router.replace`. El `Suspense` es
 * obligatorio para poder prerenderizar el esqueleto de la ruta.
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
