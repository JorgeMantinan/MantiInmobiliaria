import type { Metadata } from 'next';
import { GuardiaAdmin } from '@/features/backoffice/components/guardia-admin';

export const metadata: Metadata = {
  title: 'Backoffice | MantiInmobiliaria',
  description: 'Gestión de inmuebles, profesionales y solicitudes del portal.',
  robots: { index: false, follow: false },
};

/**
 * Backoffice: la página es server (metadata) y el guard es cliente porque
 * lee la sesión (`useSesion`) y el rol de `GET /api/v1/auth/me`.
 */
export default function AdminLayout({ children }: LayoutProps<'/admin'>) {
  return <GuardiaAdmin>{children}</GuardiaAdmin>;
}
