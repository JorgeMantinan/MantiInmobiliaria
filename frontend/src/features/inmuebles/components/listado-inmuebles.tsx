'use client';

import { Boton } from '@/components/ui/boton';
import { GuardarComoAlerta } from '@/features/alertas/components/guardar-como-alerta';
import { useListadoInmuebles } from '../hooks/use-listado-inmuebles';
import { BarraFiltros } from './barra-filtros';
import { ConmutadorVista } from './conmutador-vista';
import { ListadoCargando, ListadoError, ListadoVacio } from './estados-listado';
import { TarjetaInmueble } from './tarjeta-inmueble';
import { VistaMapa } from './vista-mapa';

/**
 * Listado público: pinta los estados (carga / error / vacío / resultados),
 * alterna entre vista Lista y vista Mapa (misma carga del hook) y delega los
 * datos y la sincronización con la URL en `useListadoInmuebles`.
 */
export function ListadoInmuebles() {
  const {
    filtros,
    consulta,
    vista,
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
    cambiarVista,
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

      {/* issue #96 — guardar la búsqueda actual como alerta (sólo con sesión). */}
      <GuardarComoAlerta filtros={filtros} />

      <div className="mt-6">
        <ConmutadorVista vista={vista} onCambiar={cambiarVista} />

        {cargando ? (
          <div className="mt-4">
            <ListadoCargando />
          </div>
        ) : error !== null ? (
          <div className="mt-4">
            <ListadoError mensaje={error} onReintentar={reintentar} />
          </div>
        ) : inmuebles.length === 0 ? (
          <div className="mt-4">
            <ListadoVacio onLimpiar={limpiar} />
          </div>
        ) : (
          <div className="mt-4">
            {vista === 'mapa' ? (
              <VistaMapa inmuebles={inmuebles} />
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
              </>
            )}
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
          </div>
        )}
      </div>
    </section>
  );
}
