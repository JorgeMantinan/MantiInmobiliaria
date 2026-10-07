/** Esqueleto de la ficha mientras Next monta el segmento. */
export default function CargandoFicha() {
  return (
    <div role="status" aria-live="polite" className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6">
      <span className="sr-only">Cargando el inmueble…</span>
      <div aria-hidden="true" className="space-y-4">
        <div className="h-8 w-64 animate-pulse rounded-md bg-superficie-alta" />
        <div className="aspect-[4/3] w-full animate-pulse rounded-lg bg-superficie-alta" />
        <div className="h-72 w-full animate-pulse rounded-md border border-borde bg-superficie-alta" />
      </div>
    </div>
  );
}
