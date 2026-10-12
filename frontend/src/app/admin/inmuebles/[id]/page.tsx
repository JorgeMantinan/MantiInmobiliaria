import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { EdicionInmueble } from '@/features/backoffice/components/edicion-inmueble';

type Props = Readonly<{ params: Promise<{ id: string }> }>;

/** Id válido: sólo dígitos y mayor que cero (coincide con el 404 de la ruta). */
function idValido(id: string): boolean {
  return /^\d+$/.test(id) && Number(id) > 0;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  return {
    title: idValido(id) ? `Editar inmueble ${id} | Backoffice` : 'Inmueble no encontrado | Backoffice',
    description: 'Edición de un anuncio de inmueble del portal.',
  };
}

/** Edición: PUT `/api/v1/admin/inmuebles/{id}` (rol ADMIN). */
export default async function AdminInmuebleEditarPage({ params }: Props) {
  const { id } = await params;
  if (!idValido(id)) {
    notFound();
  }
  return <EdicionInmueble id={Number(id)} />;
}
