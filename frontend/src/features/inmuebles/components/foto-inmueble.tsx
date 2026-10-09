'use client';

import { useState } from 'react';

type FotoInmuebleProps = Readonly<{
  /** URL https del backend; `null` cuando el inmueble no tiene imagen. */
  src: string | null;
  alt: string;
}>;

/**
 * Foto 4:3 con fallback: si no hay imagen o la URL falla se muestra una
 * superficie neutra con el mismo `alt` (nada de huecos rotos en la tarjeta).
 */
export function FotoInmueble({ src, alt }: FotoInmuebleProps) {
  const [falla, setFalla] = useState(false);

  // Sólo se renderiza https: una URL http:, javascript: o de otro protocolo
  // procedente de datos de la API se trata como "sin imagen".
  const esSegura = src !== null && /^https:\/\//i.test(src);

  if (!esSegura || falla) {
    return (
      <div
        role="img"
        aria-label={alt}
        className="flex aspect-[4/3] w-full items-center justify-center bg-superficie-alta px-4 text-center text-sm text-texto-suave"
      >
        Sin imagen disponible
      </div>
    );
  }

  return (
    // Foto con URL externa variable (backend/picsum): `next/image` exigiría
    // abrir `remotePatterns` y sólo retrasaría el render de la tarjeta.
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      alt={alt}
      loading="lazy"
      decoding="async"
      onError={() => setFalla(true)}
      className="aspect-[4/3] w-full bg-superficie-alta object-cover"
    />
  );
}
