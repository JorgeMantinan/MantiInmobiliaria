import type { Metadata } from 'next';
import { Suspense } from 'react';
import { FilasCargando } from '@/features/backoffice/components/estados-listado';
import { PanelInmuebles } from '@/features/backoffice/components/panel-inmuebles';

export const metadata: Metadata = {
  title: 'Inmuebles | Backoffice',
  description: 'Alta, edición y borrado de anuncios del portal.',
};

/**
 * Página server fina (metadata) + contenedor cliente: el panel lee los
 * filtros con `useSearchParams`, así que el `Suspense` es obligatorio para
 * poder prerenderizar la ruta.
 */
export default function AdminInmueblesPage() {
  return (
    <Suspense fallback={<FilasCargando />}>
      <PanelInmuebles />
    </Suspense>
  );
}
