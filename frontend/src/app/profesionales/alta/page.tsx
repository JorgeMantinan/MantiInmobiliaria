import type { Metadata } from 'next';
import Link from 'next/link';
import { FormularioAlta } from '@/features/profesionales/components/formulario-alta';

export const metadata: Metadata = {
  title: 'Alta de profesionales | MantiInmobiliaria',
  description:
    'Regístrate como profesional del portal: nombre, contacto y gremios. Validamos tu cuenta y recibirás las solicitudes de trabajo por email.',
};

const ENLACE =
  'rounded-md text-acento-nogal underline underline-offset-4 transition-colors outline-none hover:text-acento-hover focus:ring-2 focus:ring-acento-nogal';

/** Alta de profesional: server component (metadata) + formulario cliente. */
export default function AltaProfesionalesPage() {
  return (
    <div className="mx-auto w-full max-w-3xl px-4 py-10 sm:px-6 sm:py-14">
      <h1 className="font-display text-3xl text-texto">Alta de profesionales</h1>
      <p className="mt-3 text-texto-suave">
        Dinos quién eres y qué gremios cubres; validaremos tu cuenta y empezarás a recibir
        solicitudes de trabajo de tu zona.
      </p>
      <div className="mt-8">
        <FormularioAlta />
      </div>
      <p className="mt-8 text-sm text-texto-suave">
        ¿Necesitas contratar a un profesional?{' '}
        <Link href="/profesionales" className={ENLACE}>
          Pide presupuesto
        </Link>
      </p>
    </div>
  );
}
