import Link from 'next/link';

type CtaSesionProps = Readonly<{
  titulo: string;
  descripcion: string;
}>;

const ENLACE_PRIMARIO =
  'rounded-md bg-acento-nogal px-4 py-2 text-sm font-medium text-superficie transition-colors outline-none focus:ring-2 focus:ring-acento-nogal hover:bg-acento-hover';

const ENLACE_SECUNDARIO =
  'rounded-md border border-borde px-4 py-2 text-sm font-medium text-texto transition-colors outline-none focus:ring-2 focus:ring-acento-nogal hover:bg-superficie-alta';

/** Pantalla para usuarios anónimos: nunca se llama a la API sin sesión. */
export function CtaSesion({ titulo, descripcion }: CtaSesionProps) {
  return (
    <section className="w-full max-w-md rounded-lg border border-borde bg-superficie p-6 text-center">
      <h2 className="font-display text-2xl text-texto">{titulo}</h2>
      <p className="mt-2 text-sm text-texto-suave">{descripcion}</p>
      <div className="mt-6 flex flex-col gap-3">
        <Link href="/login" className={ENLACE_PRIMARIO}>
          Iniciar sesión
        </Link>
        <Link href="/inmuebles" className={ENLACE_SECUNDARIO}>
          Ver viviendas
        </Link>
      </div>
    </section>
  );
}
