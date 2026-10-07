import { ETIQUETAS_TIPO, formatoSuperficie } from '../formato';
import type { TipoInmueble } from '../types';

type SeccionCaracteristicasProps = Readonly<{
  superficieM2: number | null;
  habitaciones: number | null;
  banios: number | null;
  tipo: TipoInmueble;
}>;

type CaracteristicaProps = Readonly<{ etiqueta: string; valor: string }>;

function Caracteristica({ etiqueta, valor }: CaracteristicaProps) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-texto-suave">{etiqueta}</dt>
      <dd className="mt-0.5 font-medium text-texto">{valor}</dd>
    </div>
  );
}

function valorOpcion(valor: number | null, formato: (v: number) => string): string {
  return valor === null ? '—' : formato(valor);
}

/** Datos duros de la ficha: superficie, habitaciones, baños y tipo. */
export function SeccionCaracteristicas({
  superficieM2,
  habitaciones,
  banios,
  tipo,
}: SeccionCaracteristicasProps) {
  return (
    <dl className="mt-6 grid grid-cols-2 gap-4 rounded-lg border border-borde bg-superficie p-5 sm:grid-cols-4">
      <Caracteristica etiqueta="Superficie" valor={valorOpcion(superficieM2, formatoSuperficie)} />
      <Caracteristica etiqueta="Habitaciones" valor={valorOpcion(habitaciones, (v) => String(v))} />
      <Caracteristica etiqueta="Baños" valor={valorOpcion(banios, (v) => String(v))} />
      <Caracteristica etiqueta="Tipo" valor={ETIQUETAS_TIPO[tipo]} />
    </dl>
  );
}
