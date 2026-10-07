import Link from 'next/link';

/** 404 amable para ids inválidos o inexistentes (`notFound()` del segmento). */
export default function InmuebleNoEncontrado() {
  return (
    <div className="mx-auto flex w-full max-w-md flex-1 items-center justify-center px-4 py-12">
      <section className="w-full rounded-lg border border-borde bg-superficie p-6 text-center">
        <h1 className="font-display text-2xl text-texto">Inmueble no encontrado</h1>
        <p className="mt-2 text-sm text-texto-suave">
          El enlace no es correcto o el anuncio ya no está disponible.
        </p>
        <Link
          href="/inmuebles"
          className="mt-6 inline-block rounded-md bg-acento-nogal px-4 py-2 text-sm font-medium text-superficie transition-colors outline-none hover:bg-acento-hover focus:ring-2 focus:ring-acento-nogal"
        >
          Ver todas las viviendas
        </Link>
      </section>
    </div>
  );
}
