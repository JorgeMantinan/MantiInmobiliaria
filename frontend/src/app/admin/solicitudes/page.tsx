import type { Metadata } from 'next';
import { ListaSolicitudes } from '@/features/backoffice/components/lista-solicitudes';

export const metadata: Metadata = {
  title: 'Solicitudes | Backoffice',
  description: 'Últimas solicitudes de presupuesto recibidas por el portal (sólo lectura).',
};

/** Página server fina (metadata) + contenedor cliente de sólo lectura. */
export default function AdminSolicitudesPage() {
  return <ListaSolicitudes />;
}
