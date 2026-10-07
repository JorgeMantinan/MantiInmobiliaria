/** Esqueleto mientras Next resuelve el primer render de `/swipe`. */
export default function CargandoSwipe() {
  return (
    <div role="status" className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6">
      <span className="sr-only">Cargando modo swipe…</span>
      <div
        aria-hidden="true"
        className="mx-auto aspect-[4/5] w-full max-w-md animate-pulse rounded-lg border border-borde bg-superficie-alta"
      />
    </div>
  );
}
