import type { InputHTMLAttributes } from 'react';
import { AVISO_ERROR } from './avisos';

type CampoProps = Readonly<{
  id: string;
  etiqueta: string;
  /** Mensaje de validación; si existe se muestra y se marca el input. */
  error?: string;
}> &
  Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'className'>;

/** Input con label accesible y mensaje de error (`aria-describedby`). */
export function Campo({ id, etiqueta, error, ...props }: CampoProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-texto">
        {etiqueta}
      </label>
      <input
        {...props}
        id={id}
        aria-invalid={error !== undefined}
        aria-describedby={error === undefined ? undefined : `${id}-error`}
        className="rounded-md border border-borde bg-superficie px-3 py-2 text-texto outline-none transition-colors focus:ring-2 focus:ring-acento-nogal"
      />
      {error !== undefined ? (
        <p id={`${id}-error`} role="alert" className={AVISO_ERROR}>
          {error}
        </p>
      ) : null}
    </div>
  );
}
