import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { DetalleInmueble } from '@/features/inmuebles/components/detalle-inmueble';

type Props = Readonly<{ params: Promise<{ id: string }> }>;

/** Id válido: sólo dígitos y mayor que cero (coincide con el 404 de la ruta). */
function idValido(id: string): boolean {
  return /^\d+$/.test(id) && Number(id) > 0;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const descripcion =
    'Detalles, características, fotos y ubicación en el mapa de este inmueble.';
  if (!idValido(id)) {
    // Sin validar, un id arbitrario de la URL acabaría en el <title>.
    return { title: 'Inmueble no encontrado | MantiInmobiliaria', description: descripcion };
  }
  return {
    title: `Inmueble ${id} | MantiInmobiliaria`,
    description: descripcion,
    // TODO(Fase 10, cuando haya dominio propio): metadata dinámica por inmueble
    // (título, precio, fotos Open Graph y JSON-LD `RealEstateListing`) leyendo
    // `GET /api/v1/inmuebles/{id}` aquí, con `revalidate` y URL canónica.
  };
}

/** Server page fina: sólo valida el id y delega el resto en el cliente. */
export default async function PaginaInmueble({ params }: Props) {
  const { id } = await params;
  if (!idValido(id)) {
    notFound();
  }
  return <DetalleInmueble id={Number(id)} />;
}
