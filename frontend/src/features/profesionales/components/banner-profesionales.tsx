import Link from 'next/link';

/**
 * CTA del home entre destacados y la sección informativa: invita a pedir
 * presupuesto a los profesionales del portal.
 */
export function BannerProfesionales() {
  return (
    <section aria-labelledby="titulo-banner-profesionales" className="bg-fondo">
      <div className="mx-auto w-full max-w-6xl px-4 py-10 sm:px-6">
        <div className="flex flex-col gap-6 rounded-lg border border-borde bg-superficie-alta p-6 sm:flex-row sm:items-center sm:justify-between sm:p-8">
          <div className="max-w-2xl">
            <h2
              id="titulo-banner-profesionales"
              className="font-display text-2xl leading-snug text-texto sm:text-3xl"
            >
              ¿Buscas un profesional? Contacta con los nuestros
            </h2>
            <p className="mt-3 text-texto-suave">
              Fontaneros, electricistas, carpinteros y más: describe el trabajo que necesitas y
              recibe el contacto de profesionales de confianza de tu zona.
            </p>
          </div>
          <Link
            href="/profesionales"
            className="inline-block shrink-0 rounded-md bg-acento-nogal px-5 py-2.5 text-sm font-medium text-superficie transition-colors outline-none hover:bg-acento-hover focus:ring-2 focus:ring-acento-nogal"
          >
            Ver profesionales
          </Link>
        </div>
      </div>
    </section>
  );
}
