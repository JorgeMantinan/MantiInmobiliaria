'use client';

import { useMemo } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { AVISO_ERROR } from '@/components/ui/avisos';
import { Boton } from '@/components/ui/boton';
import { useInmueblesAdmin } from '../hooks/use-inmuebles-admin';
import type { FiltrosAdmin } from '../hooks/use-inmuebles-admin';
import { FilasCargando, FilasError, FilasVacias } from './estados-listado';
import { FiltrosAdminInmuebles, PestanasEstadoInmuebles } from './filtros-admin-inmuebles';
import { FilaInmueble } from './fila-inmueble';

const CLASE_ACCION =
  'inline-block rounded-md bg-acento-nogal px-4 py-2 text-sm font-medium text-superficie transition-colors outline-none hover:bg-acento-hover focus:ring-2 focus:ring-acento-nogal';

/**
 * Backoffice de inmuebles sobre el endpoint público (con filtros enlazables):
 * pestañas de estado, buscador por municipio, filtro de destacados y acciones
 * por fila (editar / eliminar en dos pasos con borrado optimista).
 */
export function PanelInmuebles() {
  const searchParams = useSearchParams();
  const {
    cargando,
    inmuebles,
    total,
    hayMas,
    cargandoMas,
    error,
    errorMas,
    aviso,
    errorAccion,
    enVuelo,
    aplicar,
    limpiar,
    reintentar,
    cargarMas,
    eliminar,
  } = useInmueblesAdmin();

  const estado = searchParams.get('estado');
  const municipio = searchParams.get('municipio') ?? '';
  const destacado = searchParams.get('destacado') === 'true';
  /** Un DELETE en vuelo: ni cambios de filtro que invaliden la fila. */
  const borradoEnVuelo = enVuelo.length > 0;

  /** Filtros vigentes: las pestañas los conservan al cambiar sólo el estado. */
  const filtros = useMemo<FiltrosAdmin>(
    () => ({
      estado: estado === 'VENTA' || estado === 'ALQUILER' ? estado : undefined,
      municipio: municipio === '' ? undefined : municipio,
      destacado: destacado ? true : undefined,
    }),
    [estado, municipio, destacado],
  );

  return (
    <section aria-labelledby="titulo-inmuebles">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 id="titulo-inmuebles" className="font-display text-3xl text-texto">
            Inmuebles
          </h1>
          <p className="mt-2 text-texto-suave">
            Gestiona los anuncios del portal: crea, edita, marca destacados o elimina (borrado
            lógico, la fila se conserva para auditoría).
          </p>
        </div>
        <Link href="/admin/inmuebles/nuevo" className={CLASE_ACCION}>
          Nuevo inmueble
        </Link>
      </div>

      <PestanasEstadoInmuebles
        estado={estado}
        onEstado={(nuevo) => aplicar({ ...filtros, estado: nuevo })}
        disabled={borradoEnVuelo}
      />
      <FiltrosAdminInmuebles
        filtros={filtros}
        onAplicar={aplicar}
        onLimpiar={limpiar}
        disabled={borradoEnVuelo}
      />

      {/* Regiones vivas del resultado de cada borrado. */}
      <div
        role="status"
        aria-live="polite"
        className={aviso === null ? 'sr-only' : 'mt-4 text-sm text-acento-hover'}
      >
        {aviso}
      </div>
      {errorAccion !== null ? (
        <p role="alert" aria-live="assertive" className={`mt-4 ${AVISO_ERROR}`}>
          {errorAccion}
        </p>
      ) : null}

      <div className="mt-6">
        {cargando ? (
          <FilasCargando />
        ) : error !== null ? (
          <FilasError
            titulo="No se han podido cargar los inmuebles"
            mensaje={error}
            onReintentar={reintentar}
            bloqueado={borradoEnVuelo}
          />
        ) : inmuebles.length === 0 ? (
          <FilasVacias
            titulo="Sin inmuebles"
            mensaje="No hay anuncios que cumplan los filtros actuales."
          />
        ) : (
          <>
            <p className="text-sm text-texto-suave">
              {`${total !== null ? (total === 1 ? '1 inmueble' : `${total} inmuebles`) : `${inmuebles.length} inmuebles cargados`}${hayMas ? ' · hay más resultados' : ''}.`}
            </p>
            <ul className="mt-4 flex list-none flex-col gap-4">
              {inmuebles.map((inmueble) => (
                <FilaInmueble
                  key={inmueble.id}
                  inmueble={inmueble}
                  ocupado={enVuelo.includes(inmueble.id)}
                  onEliminar={(fila) => void eliminar(fila)}
                />
              ))}
            </ul>
            {errorMas !== null ? (
              <p role="alert" className={`mt-4 ${AVISO_ERROR}`}>
                {errorMas}
              </p>
            ) : null}
            {hayMas ? (
              <div className="mt-6 flex justify-center">
                <Boton variante="secundario" disabled={cargandoMas} onClick={() => void cargarMas()}>
                  {cargandoMas ? 'Cargando…' : 'Cargar más'}
                </Boton>
              </div>
            ) : null}
          </>
        )}
      </div>
    </section>
  );
}
