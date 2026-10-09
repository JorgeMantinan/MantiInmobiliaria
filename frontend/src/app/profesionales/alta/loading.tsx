/** Esqueleto mientras Next resuelve el primer render de `/profesionales/alta`. */
export default function CargandoAltaProfesionales() {
  return (
    <div role="status" className="mx-auto w-full max-w-3xl px-4 py-10 sm:px-6">
      <span className="sr-only">Cargando el formulario…</span>
      <div aria-hidden="true" className="flex flex-col gap-4">
        <div className="h-9 w-2/3 animate-pulse rounded-md bg-superficie-alta" />
        <div className="h-4 w-1/2 animate-pulse rounded-md bg-superficie-alta" />
        <div className="h-64 animate-pulse rounded-lg border border-borde bg-superficie" />
      </div>
    </div>
  );
}
