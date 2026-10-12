'use client';

import type { FormEvent } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { AVISO_ERROR } from '@/components/ui/avisos';
import { Boton } from '@/components/ui/boton';
import { CLASE_CAMPO } from '@/features/inmuebles/components/campo-filtro';
import { useInmueblesAdmin } from '../hooks/use-inmuebles-admin';
import type { FiltrosAdminInmuebles } from '../hooks/use-inmuebles-admin';
import { FilasCargando, FilasError, FilasVacias } from './estados-listado';
import { FilaInmueble } from './fila-inmueble';

const CLASE_PESTANA =
  'rounded-md border px-3 py-1.5 text-sm transition-colors outline-none focus:ring-2 focus:ring-acento-nogal';

const CLASE_ACCION =
  'inline-block rounded-md bg-acento-nogal px-4 py-2 text-sm font-medium text-superficie transition-colors outline-none hover:bg-acento-hover focus:ring-2 focus:ring-acento-nogal';

type OpcionEstado = Readonly<{ valor: FiltrosAdminInmuebles['estado']; etiqueta: string }>;

const PESTANAS: readonly OpcionEstado[] = [
  { valor: undefined, etiqueta: 'Todos' },
  { valor: 'VENTA', etiqueta: 'En venta' },
  { valor: 'ALQUILER', etiqueta: 'En alquiler' },
];

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
  const municipioInicial = searchParams.get('municipio') ?? '';
  const destacadoInicial = searchParams.get('destacado') === 'true';
  /** Un DELETE en vuelo: ni cambios de filtro que invaliden la fila. */
  const borradoEnVuelo = enVuelo.length > 0;

  function aplicarMunicipio(evento: FormEvent<HTMLFormElement>): void {
    evento.preventDefault();
    const datos = new FormData(evento.currentTarget);
    const municipio = String(datos.get('municipio') ?? '').trim().slice(0, 120);
    const destacado = datos.get('destacado') === 'on';
    aplicar({
      estado: estado === 'VENTA' || estado === 'ALQUILER' ? estado : undefined,
      municipio: municipio === '' ? undefined : municipio,
      destacado: destacado ? true : undefined,
    });
  }

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

      <div role="group" aria-label="Filtrar por estado" className="mt-6 flex flex-wrap gap-2">
        {PESTANAS.map((opcion) => (
          <button
            key={opcion.etiqueta}
            type="button"
            aria-pressed={opcion.valor === undefined ? estado === null : estado === opcion.valor}
            disabled={borradoEnVuelo}
            onClick={() => aplicar({ estado: opcion.valor })}
            className={`${CLASE_PESTANA} ${
              (opcion.valor === undefined ? estado === null : estado === opcion.valor)
                ? 'border-acento-nogal bg-superficie-alta font-medium text-texto'
                : 'border-borde bg-superficie text-texto-suave hover:bg-superficie-alta'
            } ${borradoEnVuelo ? 'cursor-not-allowed opacity-60' : ''}`}
          >
            {opcion.etiqueta}
          </button>
        ))}
      </div>

      <form
        aria-label="Buscar por municipio y destacado"
        onSubmit={aplicarMunicipio}
        className="mt-4 flex flex-wrap items-end gap-3 rounded-lg border border-borde bg-superficie p-4"
      >
        <div className="flex min-w-40 flex-col gap-1.5">
          <label htmlFor="admin-municipio" className="text-sm font-medium text-texto">
            Municipio
          </label>
          <input
            id="admin-municipio"
            name="municipio"
            type="search"
            maxLength={120}
            defaultValue={municipioInicial}
            placeholder="Madrid…"
            className={CLASE_CAMPO}
          />
        </div>
        <div className="flex items-center gap-2 pb-2">
          <input
            id="admin-destacado"
            name="destacado"
            type="checkbox"
            defaultChecked={destacadoInicial}
            className="size-4 accent-acento-nogal"
          />
          <label htmlFor="admin-destacado" className="text-sm font-medium text-texto">
            Sólo destacados
          </label>
        </div>
        <div className="flex gap-2">
          <Boton type="submit" disabled={borradoEnVuelo}>
            Buscar
          </Boton>
          <Boton variante="secundario" disabled={borradoEnVuelo} onClick={limpiar}>
            Limpiar filtros
          </Boton>
        </div>
      </form>

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
