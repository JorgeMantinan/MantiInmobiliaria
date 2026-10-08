import type { TextareaHTMLAttributes } from 'react';
import { AVISO_ERROR } from './avisos';

type CampoTextoAreaProps = Readonly<{
  id: string;
  etiqueta: string;
  /** Mensaje de validación; si existe se muestra y se marca el textarea. */
  error?: string;
  /** Texto de ayuda corto bajo el campo (se enlaza con `aria-describedby`). */
  ayuda?: string;
}> &
  Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id' | 'className'>;

/** Textarea con label accesible, ayuda y mensaje de error (`aria-describedby`). */
export function CampoTextoArea({ id, etiqueta, error, ayuda, ...props }: CampoTextoAreaProps) {
  const idError = `${id}-error`;
  const idAyuda = `${id}-ayuda`;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-texto">
        {etiqueta}
      </label>
      <textarea
        {...props}
        id={id}
        aria-invalid={error !== undefined}
        aria-describedby={
          error === undefined ? (ayuda === undefined ? undefined : idAyuda) : idError
        }
        className="rounded-md border border-borde bg-superficie px-3 py-2 text-texto outline-none transition-colors focus:ring-2 focus:ring-acento-nogal"
      />
      {error !== undefined ? (
        <p id={idError} role="alert" className={AVISO_ERROR}>
          {error}
        </p>
      ) : null}
      {ayuda !== undefined ? <p id={idAyuda} className="text-xs text-texto-suave">{ayuda}</p> : null}
    </div>
  );
}
