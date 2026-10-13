'use client';

import Link from 'next/link';
import { Boton } from '@/components/ui/boton';
import { CtaSesion } from '@/features/swipes/components/cta-sesion';
import { ENLACE_PRIMARIO } from '@/features/swipes/clases';
import { useAlertas } from '../hooks/use-alertas';
import { TarjetaAlerta } from './tarjeta-alerta';

/** Esqueleto mientras llega la primera carga (`role="status"` ya anuncia). */
function AlertasCargando() {
  return (
    <div role="status">
      <span className="sr-only">Cargando alertas…</span>
      <ul aria-hidden="true" className="grid list-none gap-6 sm:grid-cols-2">
        {Array.from({ length: 4 }, (_, indice) => (
          <li
            key={indice}
            className="h-40 animate-pulse rounded-lg border border-borde bg-superficie-alta"
          />
        ))}
      </ul>
    </div>
  );
}

/**
 * "Mis alertas" (issue #96): lista las alertas del usuario con estados
 * carga / error / vacío, y las acciones pausar-activar y eliminar con su
 * feedback (mismo patrón de estados que el listado de favoritos).
 */
export function ListadoAlertas() {
  const {
    sinSesion,
    cargando,
    error,
    alertas,
    errorAccion,
    cambiando,
    alternar,
    eliminar,
    reintentar,
  } = useAlertas();

  return (
    <section
      aria-labelledby="titulo-alertas"
      className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6"
    >
      <header className="mb-6">
        <h1 id="titulo-alertas" className="font-display text-3xl text-texto">
          Mis alertas
        </h1>
        <p className="mt-1 text-sm text-texto-suave">
          Búsquedas guardadas: te avisamos cuando entre un inmueble nuevo que encaja.
        </p>
      </header>

      {sinSesion ? (
        <CtaSesion
          titulo="Inicia sesión para gestionar tus alertas"
          descripcion="Con tu cuenta podrás guardar búsquedas y recibir avisos de las viviendas nuevas."
        />
      ) : cargando ? (
        <AlertasCargando />
      ) : error !== null ? (
        <div
          role="alert"
          className="rounded-lg border border-borde bg-superficie p-6 text-center"
        >
          <h2 className="font-display text-xl text-texto">No se han podido cargar tus alertas</h2>
          <p className="mt-2 text-sm text-texto-suave">{error}</p>
          <div className="mt-4 flex justify-center">
            <Boton onClick={reintentar}>Reintentar</Boton>
          </div>
        </div>
      ) : alertas.length === 0 ? (
        <div className="rounded-lg border border-borde bg-superficie p-6 text-center">
          <h2 className="font-display text-xl text-texto">Aún no tienes alertas</h2>
          <p className="mt-2 text-sm text-texto-suave">
            Filtra el listado de viviendas y pulsa “Guardar como alerta” para recibir avisos.
          </p>
          <div className="mt-4 flex flex-col gap-3">
            <Link href="/inmuebles" className={ENLACE_PRIMARIO}>
              Buscar viviendas
            </Link>
          </div>
        </div>
      ) : (
        <>
          <p role="status" className="text-sm text-texto-suave">
            Mostrando {alertas.length} {alertas.length === 1 ? 'alerta' : 'alertas'}
          </p>
          {errorAccion !== null ? (
            <p role="alert" className="mt-3 text-sm text-acento-hover">
              {errorAccion}
            </p>
          ) : null}

          <ul className="mt-4 grid list-none gap-6 sm:grid-cols-2">
            {alertas.map((alerta) => (
              <li key={alerta.id} className="rounded-lg border border-borde bg-superficie p-5">
                <TarjetaAlerta
                  alerta={alerta}
                  ocupada={cambiando.has(alerta.id)}
                  onAlternar={alternar}
                  onEliminar={eliminar}
                />
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}
