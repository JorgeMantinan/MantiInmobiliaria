'use client';

import Link from 'next/link';
import { Boton } from '@/components/ui/boton';

type ErrorSegmentoProps = Readonly<{
  error: Error & { digest?: string };
  retry: () => void;
}>;

/** Fallback mínimo compartido por los `error.tsx` de los segmentos de auth. */
export function ErrorSegmento({ error, retry }: ErrorSegmentoProps) {
  return (
    <div className="flex flex-1 items-center justify-center bg-fondo px-4 py-12">
      <section className="w-full max-w-md rounded-lg border border-borde bg-superficie p-6 text-center sm:p-8">
        <h1 className="font-display text-2xl text-texto">Algo ha ido mal</h1>
        <p role="alert" className="mt-2 text-sm text-texto-suave">
          {error.message}
        </p>
        <div className="mt-6 flex flex-col gap-3">
          <Boton onClick={retry}>Reintentar</Boton>
          <Link
            href="/"
            className="rounded-md border border-borde px-4 py-2 text-sm font-medium text-texto transition-colors outline-none focus:ring-2 focus:ring-acento-nogal hover:bg-superficie-alta"
          >
            Volver al inicio
          </Link>
        </div>
      </section>
    </div>
  );
}
