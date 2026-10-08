import type { Metadata } from 'next';
import Link from 'next/link';
import { FormularioSolicitud } from '@/features/profesionales/components/formulario-solicitud';

export const metadata: Metadata = {
  title: 'Pide presupuesto a profesionales | MantiInmobiliaria',
  description:
    'Describe el trabajo que necesitas, elige los gremios y recibe el contacto de profesionales de tu municipio: fontaneros, electricistas, albañiles, carpinteros y más.',
};

const ENLACE =
  'rounded-md text-acento-nogal underline underline-offset-4 transition-colors outline-none hover:text-acento-hover focus:ring-2 focus:ring-acento-nogal';

/**
 * Página pública de solicitudes: server component (metadata estática) con el
 * formulario de solicitud, que es cliente porque gestiona estado y llamadas.
 */
export default function ProfesionalesPage() {
  return (
    <div className="mx-auto w-full max-w-3xl px-4 py-10 sm:px-6 sm:py-14">
      <h1 className="font-display text-3xl text-texto">Pide presupuesto a profesionales</h1>
      <p className="mt-3 text-texto-suave">
        Cuéntanos qué trabajo necesitas y elige los gremios; los profesionales que pueda cubrirlo
        recibirán tu solicitud y se pondrán en contacto contigo.
      </p>
      <div className="mt-8">
        <FormularioSolicitud />
      </div>
      <p className="mt-8 text-sm text-texto-suave">
        ¿Eres profesional?{' '}
        <Link href="/profesionales/alta" className={ENLACE}>
          Regístrate
        </Link>
      </p>
    </div>
  );
}
