import Link from 'next/link';

type ConfirmacionProps = Readonly<{
  titulo: string;
  /** Gremios elegidos, ya resueltos a nombre legible (puede venir vacío). */
  resumen: readonly string[];
  enlace: Readonly<{ href: string; texto: string }>;
}>;

/** Pantalla de éxito compartida por solicitud y alta (`aria-live` polite). */
export function Confirmacion({ titulo, resumen, enlace }: ConfirmacionProps) {
  return (
    <section
      role="status"
      aria-live="polite"
      className="rounded-lg border border-borde bg-superficie-alta p-6 text-center sm:p-8"
    >
      <h2 className="font-display text-2xl text-texto">{titulo}</h2>
      {resumen.length > 0 ? (
        <div className="mt-4">
          <h3 className="text-sm font-medium text-texto">Gremios seleccionados</h3>
          <ul className="mt-2 flex list-none flex-wrap justify-center gap-2">
            {resumen.map((nombre) => (
              <li
                key={nombre}
                className="rounded-md border border-borde bg-superficie px-2.5 py-1 text-sm text-texto"
              >
                {nombre}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      <div className="mt-6">
        <Link
          href={enlace.href}
          className="inline-block rounded-md bg-acento-nogal px-4 py-2 text-sm font-medium text-superficie transition-colors outline-none hover:bg-acento-hover focus:ring-2 focus:ring-acento-nogal"
        >
          {enlace.texto}
        </Link>
      </div>
    </section>
  );
}
