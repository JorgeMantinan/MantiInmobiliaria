'use client';

import { ErrorSegmento } from '@/components/ui/error-segmento';

type Props = Readonly<{ error: Error & { digest?: string }; retry: () => void }>;

/** Error de render de la ficha (los fallos de la API los gestiona la ficha). */
export default function ErrorFicha({ error, retry }: Props) {
  return <ErrorSegmento error={error} retry={retry} />;
}
