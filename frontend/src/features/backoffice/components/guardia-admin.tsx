'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';
import { useSesion } from '@/features/auth/session';

type GuardiaAdminProps = Readonly<{ children: ReactNode }>;

type SeccionAdmin = Readonly<{ href: string; etiqueta: string; exacto?: boolean }>;

const SECCIONES: readonly SeccionAdmin[] = [
  { href: '/admin', etiqueta: 'Resumen', exacto: true },
  { href: '/admin/profesionales', etiqueta: 'Profesionales' },
  { href: '/admin/solicitudes', etiqueta: 'Solicitudes' },
];

const CONTENEDOR = 'mx-auto w-full max-w-6xl px-4 py-8 sm:px-6';

const CTA_PRIMARIA =
  'inline-block rounded-md bg-acento-nogal px-4 py-2 text-sm font-medium text-superficie transition-colors outline-none hover:bg-acento-hover focus:ring-2 focus:ring-acento-nogal';

const CTA_SECUNDARIA =
  'inline-block rounded-md border border-borde px-4 py-2 text-sm font-medium text-texto transition-colors outline-none hover:bg-superficie-alta focus:ring-2 focus:ring-acento-nogal';

/**
 * Guard de `app/admin/layout.tsx`: carga → esqueleto; sin sesión → CTA a
 * `/login`; con un rol distinto de `ADMIN` → 403. Sólo con `ADMIN` se pinta
 * la navegación del backoffice y el contenido de la ruta.
 */
export function GuardiaAdmin({ children }: GuardiaAdminProps) {
  const { usuario, cargando } = useSesion();

  if (cargando) return <EsqueletoGuardia />;
  if (usuario === null) return <SesionRequerida />;
  if (usuario.rol !== 'ADMIN') return <AccesoDenegado />;

  return (
    <div className={CONTENEDOR}>
      <BarraBackoffice />
      {children}
    </div>
  );
}

function EsqueletoGuardia() {
  return (
    <div className={CONTENEDOR} role="status">
      <span className="sr-only">Comprobando permisos del backoffice…</span>
      <div
        aria-hidden="true"
        className="h-64 animate-pulse rounded-lg border border-borde bg-superficie-alta"
      />
    </div>
  );
}

function SesionRequerida() {
  return (
    <div className={`${CONTENEDOR} flex justify-center py-16`}>
      <section className="w-full max-w-md rounded-lg border border-borde bg-superficie p-6 text-center sm:p-8">
        <h1 className="font-display text-2xl text-texto">Backoffice</h1>
        <p className="mt-2 text-sm text-texto-suave">
          Necesitas iniciar sesión con una cuenta de administrador para entrar en el backoffice.
        </p>
        <p className="mt-6">
          <Link href="/login" className={CTA_PRIMARIA}>
            Inicia sesión
          </Link>
        </p>
      </section>
    </div>
  );
}

function AccesoDenegado() {
  return (
    <div className={`${CONTENEDOR} flex justify-center py-16`}>
      <section role="alert" className="w-full max-w-md rounded-lg border border-borde bg-superficie p-6 text-center sm:p-8">
        <p className="text-sm font-medium text-acento-hover">Error 403</p>
        <h1 className="mt-1 font-display text-2xl text-texto">Acceso denegado</h1>
        <p className="mt-2 text-sm text-texto-suave">
          Tu cuenta no tiene el rol de administrador, así que no puedes entrar en el backoffice.
        </p>
        <p className="mt-6">
          <Link href="/" className={CTA_SECUNDARIA}>
            Volver al inicio
          </Link>
        </p>
      </section>
    </div>
  );
}

function BarraBackoffice() {
  const ruta = usePathname();

  return (
    <nav aria-label="Secciones del backoffice" className="flex flex-wrap gap-2 border-b border-borde pb-4">
      {SECCIONES.map((seccion) => {
        const activa = seccion.exacto === true ? ruta === seccion.href : ruta.startsWith(seccion.href);
        return (
          <Link
            key={seccion.href}
            href={seccion.href}
            aria-current={activa ? 'page' : undefined}
            className={`rounded-md px-3 py-1.5 text-sm transition-colors outline-none focus:ring-2 focus:ring-acento-nogal ${
              activa
                ? 'bg-acento-nogal text-superficie'
                : 'text-texto hover:bg-superficie-alta'
            }`}
          >
            {seccion.etiqueta}
          </Link>
        );
      })}
    </nav>
  );
}
