import type { Metadata } from 'next';
import { ModoSwipe } from '@/features/swipes/components/modo-swipe';

export const metadata: Metadata = {
  title: 'Modo swipe | MantiInmobiliaria',
  description:
    'Descubre viviendas de un modo rápido: arrastra a la derecha para guardarlas como favoritas y a la izquierda para descartarlas.',
};

/**
 * La página es server (para exportar `metadata`) y el modo swipe es cliente
 * porque gestiona el deck, el arrastre y la sesión con `useSesion`.
 */
export default function SwipePage() {
  return <ModoSwipe />;
}
