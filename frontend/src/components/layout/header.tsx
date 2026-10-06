'use client';

import Link from 'next/link';
import { Boton } from '@/components/ui/boton';
import { useSesion } from '@/features/auth/session';

const ENLACE = 'rounded-md px-2 py-1 text-sm text-texto transition-colors hover:bg-superficie-alta focus:ring-2 focus:ring-acento-nogal';

/** Cabecera del portal con el widget de sesión (anónimo o identificado). */
export function Header() {
  const { usuario, cargando, cerrarSesion } = useSesion();

  return (
    <header className="border-b border-borde bg-superficie">
      <div className="mx-auto flex w-full max-w-6xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
        <Link href="/" className="rounded-md font-display text-xl text-texto focus:ring-2 focus:ring-acento-nogal">
          MantiInmobiliaria
        </Link>
        <nav aria-label="Cuenta" className="flex items-center gap-2">
          {cargando ? (
            <span role="status" className="text-sm text-texto-suave">
              Comprobando sesión…
            </span>
          ) : usuario ? (
            <>
              <span className="hidden max-w-48 truncate text-sm text-texto-suave sm:inline" title={usuario.nombre}>
                {usuario.email}
              </span>
              <Boton variante="secundario" onClick={() => void cerrarSesion()}>
                Salir
              </Boton>
            </>
          ) : (
            <>
              <Link href="/login" className={ENLACE}>
                Iniciar sesión
              </Link>
              <Link href="/registro" className={`${ENLACE} bg-acento-nogal text-superficie hover:bg-acento-hover`}>
                Crear cuenta
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
