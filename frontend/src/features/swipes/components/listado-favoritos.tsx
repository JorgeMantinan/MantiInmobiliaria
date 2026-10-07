'use client';

import Link from 'next/link';
import { Boton } from '@/components/ui/boton';
import { ListadoCargando, ListadoError } from '@/features/inmuebles/components/estados-listado';
import { TarjetaInmueble } from '@/features/inmuebles/components/tarjeta-inmueble';
import { ENLACE_PRIMARIO, ENLACE_SECUNDARIO } from '../clases';
import { useFavoritos } from '../hooks/use-favoritos';
import { CtaSesion } from './cta-sesion';

/**
 * Grid de favoritos: reutiliza `TarjetaInmueble` y añade por tarjeta el botón
 * "Quitar de favoritos" (DELETE confirmado + refresco de la lista local),
 * con estados carga / error / vacío y paginación por `nextCursor`.
 */
export function ListadoFavoritos() {
  const {
    sinSesion,
    cargando,
    error,
    favoritos,
    hayMas,
    cargandoMas,
    errorMas,
    errorQuitar,
    quitando,
    quitar,
    cargarMas,
    reintentar,
  } = useFavoritos();

  return (
    <section
      aria-labelledby="titulo-favoritos"
      className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6"
    >
      <header className="mb-6">
        <h1 id="titulo-favoritos" className="font-display text-3xl text-texto">
          Favoritos
        </h1>
        <p className="mt-1 text-sm text-texto-suave">
          Las viviendas que has guardado desde el modo swipe.
        </p>
      </header>

      {sinSesion ? (
        <CtaSesion
          titulo="Inicia sesión para guardar tus favoritos"
          descripcion="Con tu cuenta podrás revisar viviendas y llevar la cuenta de tus favoritos."
        />
      ) : cargando ? (
        <ListadoCargando />
      ) : error !== null ? (
        <ListadoError mensaje={error} onReintentar={reintentar} />
      ) : favoritos.length === 0 ? (
        <div className="rounded-lg border border-borde bg-superficie p-6 text-center">
          <h2 className="font-display text-xl text-texto">Aún no tienes favoritos</h2>
          <p className="mt-2 text-sm text-texto-suave">
            Descubre viviendas en el modo swipe y guarda las que más te gusten.
          </p>
          <div className="mt-4 flex flex-col gap-3">
            <Link href="/swipe" className={ENLACE_PRIMARIO}>
              Empezar a swipear
            </Link>
            <Link href="/inmuebles" className={ENLACE_SECUNDARIO}>
              Ver todas las viviendas
            </Link>
          </div>
        </div>
      ) : (
        <>
          <p role="status" className="text-sm text-texto-suave">
            Mostrando {favoritos.length} {favoritos.length === 1 ? 'favorito' : 'favoritos'}
          </p>

          <ul className="mt-4 grid list-none gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {favoritos.map((favorito) => (
              <li key={favorito.id} className="flex h-full flex-col gap-3">
                <TarjetaInmueble inmueble={favorito} />
                <Boton
                  variante="secundario"
                  onClick={() => quitar(favorito.id)}
                  disabled={quitando.has(favorito.id)}
                  aria-label={`Quitar de favoritos: ${favorito.titulo}`}
                >
                  {quitando.has(favorito.id) ? 'Quitando…' : 'Quitar de favoritos'}
                </Boton>
              </li>
            ))}
          </ul>

          {errorQuitar !== null ? (
            <p role="alert" className="mt-4 text-center text-sm text-texto">
              {errorQuitar}
            </p>
          ) : null}
          {errorMas !== null ? (
            <p role="alert" className="mt-4 text-center text-sm text-acento-hover">
              {errorMas}
            </p>
          ) : null}

          {hayMas ? (
            <div className="mt-8 flex justify-center">
              <Boton onClick={() => void cargarMas()} disabled={cargandoMas}>
                {cargandoMas ? 'Cargando…' : 'Cargar más'}
              </Boton>
            </div>
          ) : null}
        </>
      )}
    </section>
  );
}
