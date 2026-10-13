import type { Metadata } from 'next';
import { ListadoAlertas } from '@/features/alertas/components/listado-alertas';

export const metadata: Metadata = {
  title: 'Mis alertas | MantiInmobiliaria',
  description:
    'Tus búsquedas guardadas: pausa, reactiva o elimina las alertas que te avisan de las viviendas nuevas que encajan con tus filtros.',
};

/**
 * La página es server (para exportar `metadata`); el listado es cliente porque
 * consume la API de alertas con sesión.
 */
export default function AlertasPage() {
  return <ListadoAlertas />;
}
