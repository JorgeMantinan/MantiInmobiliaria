import type { ButtonHTMLAttributes, ReactNode } from 'react';

type BotonProps = Readonly<{
  variante?: 'primario' | 'secundario';
  children: ReactNode;
}> &
  Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className'>;

const CLASES: Record<'primario' | 'secundario', string> = {
  primario: 'bg-acento-nogal text-superficie hover:bg-acento-hover',
  secundario: 'border border-borde bg-transparent text-texto hover:bg-superficie-alta',
};

/** Botón base del sistema de diseño madera (focus ring, sin sombras fuertes). */
export function Boton({ variante = 'primario', children, ...props }: BotonProps) {
  return (
    <button
      {...props}
      type={props.type ?? 'button'}
      className={`rounded-md px-4 py-2 text-sm font-medium transition-colors outline-none focus:ring-2 focus:ring-acento-nogal disabled:cursor-not-allowed disabled:opacity-60 ${CLASES[variante]}`}
    >
      {children}
    </button>
  );
}
