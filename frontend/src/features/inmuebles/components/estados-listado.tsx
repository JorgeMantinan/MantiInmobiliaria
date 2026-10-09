import { Boton } from '@/components/ui/boton';

type ListadoErrorProps = Readonly<{
  mensaje: string;
  onReintentar: () => void;
}>;

type ListadoVacioProps = Readonly<{
  onLimpiar: () => void;
}>;

/** Esqueleto del listado mientras llega la primera página (`role="status"` ya anuncia). */
export function ListadoCargando() {
  return (
    <div role="status">
      <span className="sr-only">Cargando inmuebles…</span>
      <ul aria-hidden="true" className="grid list-none gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 6 }, (_, indice) => (
          <li
            key={indice}
            className="h-96 animate-pulse rounded-lg border border-borde bg-superficie-alta"
          />
        ))}
      </ul>
    </div>
  );
}

/** Error de carga del listado, con reintento (`role="alert"` ya es live). */
export function ListadoError({ mensaje, onReintentar }: ListadoErrorProps) {
  return (
    <div
      role="alert"
      className="rounded-lg border border-borde bg-superficie p-6 text-center"
    >
      <h2 className="font-display text-xl text-texto">No se han podido cargar los inmuebles</h2>
      <p className="mt-2 text-sm text-texto-suave">{mensaje}</p>
      <div className="mt-4 flex justify-center">
        <Boton onClick={onReintentar}>Reintentar</Boton>
      </div>
    </div>
  );
}

/** Ningún inmueble cumple los filtros: se ofrecen a quitarlos. */
export function ListadoVacio({ onLimpiar }: ListadoVacioProps) {
  return (
    <div className="rounded-lg border border-borde bg-superficie p-6 text-center">
      <h2 className="font-display text-xl text-texto">Sin resultados</h2>
      <p className="mt-2 text-sm text-texto-suave">
        No hay inmuebles que coincidan con estos filtros; prueba a ampliar la búsqueda.
      </p>
      <div className="mt-4 flex justify-center">
        <Boton variante="secundario" onClick={onLimpiar}>
          Limpiar filtros
        </Boton>
      </div>
    </div>
  );
}
