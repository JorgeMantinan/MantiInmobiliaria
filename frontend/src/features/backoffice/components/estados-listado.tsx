import { Boton } from '@/components/ui/boton';

type FilasErrorProps = Readonly<{
  titulo: string;
  mensaje: string;
  onReintentar: () => void;
  /** true ⇒ bloquea el reintento (p. ej. con una moderación en vuelo). */
  bloqueado?: boolean;
}>;

type FilasVaciasProps = Readonly<{
  titulo: string;
  mensaje: string;
}>;

/** Esqueleto de las filas mientras llega el listado (`role="status"` ya anuncia). */
export function FilasCargando() {
  return (
    <div role="status">
      <span className="sr-only">Cargando listado…</span>
      <ul aria-hidden="true" className="flex list-none flex-col gap-4">
        {Array.from({ length: 4 }, (_, indice) => (
          <li
            key={indice}
            className="h-32 animate-pulse rounded-lg border border-borde bg-superficie-alta"
          />
        ))}
      </ul>
    </div>
  );
}

/** Error de carga del listado, con reintento (`role="alert"` ya es live). */
export function FilasError({
  titulo,
  mensaje,
  onReintentar,
  bloqueado = false,
}: FilasErrorProps) {
  return (
    <div role="alert" className="rounded-lg border border-borde bg-superficie p-6 text-center">
      <h2 className="font-display text-xl text-texto">{titulo}</h2>
      <p className="mt-2 text-sm text-texto-suave">{mensaje}</p>
      <div className="mt-4 flex justify-center">
        <Boton onClick={onReintentar} disabled={bloqueado}>
          Reintentar
        </Boton>
      </div>
    </div>
  );
}

/** El listado está vacío (pestaña sin resultados o sin solicitudes aún). */
export function FilasVacias({ titulo, mensaje }: FilasVaciasProps) {
  return (
    <div className="rounded-lg border border-borde bg-superficie p-6 text-center">
      <h2 className="font-display text-xl text-texto">{titulo}</h2>
      <p className="mt-2 text-sm text-texto-suave">{mensaje}</p>
    </div>
  );
}
