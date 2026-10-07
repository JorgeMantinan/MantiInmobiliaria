import type { Metadata } from 'next';
import { ListadoFavoritos } from '@/features/swipes/components/listado-favoritos';

export const metadata: Metadata = {
  title: 'Favoritos | MantiInmobiliaria',
  description:
    'Tus viviendas guardadas desde el modo swipe: revísalas, ábrevelas o quítalas de favoritos.',
};

/**
 * La página es server (para exportar `metadata`); el listado es cliente
 * porque consume la API de favoritos con sesión y cursor.
 */
export default function FavoritosPage() {
  return <ListadoFavoritos />;
}
