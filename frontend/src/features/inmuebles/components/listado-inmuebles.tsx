'use client';

import { Boton } from '@/components/ui/boton';
import { useListadoInmuebles } from '../hooks/use-listado-inmuebles';
import { BarraFiltros } from './barra-filtros';
import { ListadoCargando, ListadoError, ListadoVacio } from './estados-listado';
import { TarjetaInmueble } from './tarjeta-inmueble';

/**
 * Listado público: pinta los estados (carga / error / vacío / resultados) y
 * delega los datos y la sincronización con la URL en `useListadoInmuebles`.
 */
export function ListadoInmuebles() {
  const {
    filtros,
    consulta,
    cargando,
    inmuebles,
    total,
    hayMas,
    cargandoMas,
    error,
    errorMas,
    aplicar,
    limpiar,
    reintentar,
    cargarMas,
  } = useListadoInmuebles();

  return (
    <section aria-labelledby="titulo-listado" className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6">
      <header className="mb-6">
        <h1 id="titulo-listado" className="font-display text-3xl text-texto">
          Viviendas
        </h1>
        <p className="mt-1 text-sm text-texto-suave">
          Filtra por estado, tipo, municipio, precio o número de habitaciones.
        </p>
      </header>

      <BarraFiltros key={consulta} filtros={filtros} onAplicar={aplicar} onLimpiar={limpiar} />

      <div className="mt-6">
        {cargando ? (
          <ListadoCargando />
        ) : error !== null ? (
          <ListadoError mensaje={error} onReintentar={reintentar} />
        ) : inmuebles.length === 0 ? (
          <ListadoVacio onLimpiar={limpiar} />
        ) : (
          <>
            <p role="status" className="text-sm text-texto-suave">
              Mostrando {inmuebles.length} de {total} inmuebles
            </p>
            <ul className="mt-4 grid list-none gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {inmuebles.map((inmueble) => (
                <li key={inmueble.id} className="h-full">
                  <TarjetaInmueble inmueble={inmueble} />
                </li>
              ))}
            </ul>
            {errorMas !== null ? (
              <p role="alert" aria-live="polite" className="mt-4 text-center text-sm text-acento-hover">
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
      </div>
    </section>
  );
}
