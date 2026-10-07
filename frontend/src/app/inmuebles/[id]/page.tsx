import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { DetalleInmueble } from '@/features/inmuebles/components/detalle-inmueble';

type Props = Readonly<{ params: Promise<{ id: string }> }>;

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  return {
    title: `Inmueble ${id} | MantiInmobiliaria`,
    description:
      'Detalles, características, fotos y ubicación en el mapa de este inmueble.',
    // TODO(Fase 10, cuando haya dominio propio): metadata dinámica por inmueble
    // (título, precio, fotos Open Graph y JSON-LD `RealEstateListing`) leyendo
    // `GET /api/v1/inmuebles/{id}` aquí, con `revalidate` y URL canónica.
  };
}

/** Server page fina: sólo valida el id y delega el resto en el cliente. */
export default async function PaginaInmueble({ params }: Props) {
  const { id } = await params;
  const identificador = Number(id);
  if (!Number.isInteger(identificador) || identificador <= 0) {
    notFound();
  }
  return <DetalleInmueble id={identificador} />;
}
