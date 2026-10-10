'use client';

import { ErrorSegmento } from '@/components/ui/error-segmento';

type Props = Readonly<{ error: Error & { digest?: string }; retry: () => void }>;

export default function ErrorCallbackGoogle({ error, retry }: Props) {
  return <ErrorSegmento error={error} retry={retry} />;
}
