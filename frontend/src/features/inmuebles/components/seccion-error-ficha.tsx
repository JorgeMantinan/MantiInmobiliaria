import Link from 'next/link';

type SeccionErrorFichaProps = Readonly<{
  /** true ⇒ 404 (sin reintento); false ⇒ fallo de carga con reintento. */
  noEncontrado: boolean;
  /** Mensaje del fallo; sólo se muestra si no es un 404. */
  mensaje: string;
  onReintentar: () => void;
}>;

/** Pantalla de error/404 de la ficha, fuera del artículo para no inflarlo. */
export function SeccionErrorFicha({ noEncontrado, mensaje, onReintentar }: SeccionErrorFichaProps) {
  return (
    <div className="mx-auto flex w-full max-w-md flex-1 items-center justify-center px-4 py-12">
      <section className="w-full rounded-lg border border-borde bg-superficie p-6 text-center">
        <h1 className="font-display text-2xl text-texto">
          {noEncontrado ? 'Inmueble no encontrado' : 'No se ha podido cargar la ficha'}
        </h1>
        <p role="alert" className="mt-2 text-sm text-texto-suave">
          {noEncontrado
            ? 'Puede que se haya retirado el anuncio o que el enlace no sea correcto.'
            : mensaje}
        </p>
        {!noEncontrado ? (
          <div className="mt-6 flex flex-col gap-3">
            <button
              type="button"
              onClick={onReintentar}
              className="rounded-md bg-acento-nogal px-4 py-2 text-sm font-medium text-superficie transition-colors outline-none hover:bg-acento-hover focus:ring-2 focus:ring-acento-nogal"
            >
              Reintentar
            </button>
          </div>
        ) : null}
        <Link
          href="/inmuebles"
          className="mt-4 inline-block rounded-md border border-borde px-4 py-2 text-sm font-medium text-texto transition-colors outline-none hover:bg-superficie-alta focus:ring-2 focus:ring-acento-nogal"
        >
          Ver todas las viviendas
        </Link>
      </section>
    </div>
  );
}
