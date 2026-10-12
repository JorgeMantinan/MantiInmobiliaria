import type { Metadata } from 'next';
import Link from 'next/link';

export const metadata: Metadata = {
  title: 'Backoffice | MantiInmobiliaria',
  description: 'Panel de administración de inmuebles, profesionales y solicitudes.',
};

const TARJETAS = [
  {
    href: '/admin/inmuebles',
    titulo: 'Inmuebles',
    descripcion:
      'Alta, edición y borrado de anuncios: filtra por estado y municipio, marca destacados y gestiona precios y coordenadas.',
  },
  {
    href: '/admin/profesionales',
    titulo: 'Profesionales',
    descripcion:
      'Aprueba o rechaza las altas de profesionales: pendientes, activos y rechazados, con sus gremios.',
  },
  {
    href: '/admin/solicitudes',
    titulo: 'Solicitudes',
    descripcion:
      'Consulta las últimas solicitudes de presupuesto recibidas: contacto, municipio, gremios y descripción.',
  },
] as const;

/** Dashboard del backoffice: sólo enlaces (los datos se cargan en cada sección). */
export default function AdminPage() {
  return (
    <section aria-labelledby="titulo-admin">
      <h1 id="titulo-admin" className="font-display text-3xl text-texto">
        Backoffice
      </h1>
      <p className="mt-2 text-texto-suave">
        Selecciona una sección para gestionar el portal. Requiere rol de administrador.
      </p>

      <ul className="mt-8 grid list-none gap-6 sm:grid-cols-2">
        {TARJETAS.map((tarjeta) => (
          <li key={tarjeta.href}>
            <Link
              href={tarjeta.href}
              className="block h-full rounded-lg border border-borde bg-superficie p-6 transition-colors outline-none hover:border-acento-nogal focus:ring-2 focus:ring-acento-nogal"
            >
              <h2 className="font-display text-xl text-texto">{tarjeta.titulo}</h2>
              <p className="mt-2 text-sm text-texto-suave">{tarjeta.descripcion}</p>
              <span className="mt-4 inline-block text-sm font-medium text-acento-nogal">
                Abrir sección
              </span>
            </Link>
          </li>
        ))}
      </ul>

      <p className="mt-8 rounded-lg border border-borde bg-superficie-alta p-4 text-sm text-texto-suave">
        Los datos se cargan al abrir cada sección; este panel no muestra conteos
        (los endpoints de resumen todavía no existen).
      </p>
    </section>
  );
}
