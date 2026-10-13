import Link from 'next/link';
import { ENLACE_PRIMARIO, ENLACE_SECUNDARIO } from '@/components/ui/enlaces';

type CtaSesionProps = Readonly<{
  titulo: string;
  descripcion: string;
}>;

/**
 * Pantalla para usuarios anónimos: nunca se llama a la API sin sesión.
 * Primitiva compartida (follow-up #98): la usan swipes y alertas.
 */
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
