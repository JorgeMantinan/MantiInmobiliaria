'use client';

import { ErrorSegmento } from '@/components/ui/error-segmento';

type Props = Readonly<{ error: Error & { digest?: string }; retry: () => void }>;

/** Error de render del listado (los fallos de la API los gestiona el propio listado). */
export default function ErrorListado({ error, retry }: Props) {
  return <ErrorSegmento error={error} retry={retry} />;
}
