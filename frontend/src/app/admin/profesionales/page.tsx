import type { Metadata } from 'next';
import { Suspense } from 'react';
import { FilasCargando } from '@/features/backoffice/components/estados-listado';
import { PanelProfesionales } from '@/features/backoffice/components/panel-profesionales';

export const metadata: Metadata = {
  title: 'Profesionales | Backoffice',
  description: 'Moderación de altas de profesionales: aprobar o rechazar por estado.',
};

/**
 * Página server fina (metadata) + contenedor cliente: el panel lee
 * `?estado=` con `useSearchParams`, así que el `Suspense` es obligatorio
 * para poder prerenderizar la ruta.
 */
export default function AdminProfesionalesPage() {
  return (
    <Suspense fallback={<FilasCargando />}>
      <PanelProfesionales />
    </Suspense>
  );
}
