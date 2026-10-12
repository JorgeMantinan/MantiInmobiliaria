import type { Metadata } from 'next';
import { FormularioInmueble } from '@/features/backoffice/components/formulario-inmueble';

export const metadata: Metadata = {
  title: 'Nuevo inmueble | Backoffice',
  description: 'Alta de un anuncio de inmueble en el portal.',
};

/** Alta: POST `/api/v1/admin/inmuebles` (rol ADMIN). */
export default function AdminInmuebleNuevoPage() {
  return (
    <section aria-labelledby="titulo-nuevo">
      <h1 id="titulo-nuevo" className="font-display text-3xl text-texto">
        Nuevo inmueble
      </h1>
      <p className="mt-2 text-texto-suave">
        Crea un anuncio nuevo. Los campos marcados son obligatorios; el precio admite hasta 2
        decimales y las coordenadas deben corresponder a España.
      </p>
      <div className="mt-6">
        <FormularioInmueble inicial={null} />
      </div>
    </section>
  );
}
