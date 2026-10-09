'use client';

import { useSolicitudesAdmin } from '../hooks/use-solicitudes-admin';
import { FilasCargando, FilasError, FilasVacias } from './estados-listado';
import { FilaSolicitud } from './fila-solicitud';

/**
 * Listado de solicitudes (sólo lectura).
 *
 * TODO: el backend todavía no expone el cierre de una solicitud
 * (`PATCH /api/v1/admin/solicitudes/{id}` no existe) — issue pendiente;
 * mientras tanto aquí NO se pintan acciones de estado.
 */
export function ListaSolicitudes() {
  const { cargando, solicitudes, error, reintentar } = useSolicitudesAdmin();

  return (
    <section aria-labelledby="titulo-solicitudes">
      <h1 id="titulo-solicitudes" className="font-display text-3xl text-texto">
        Solicitudes
      </h1>
      <p className="mt-2 text-texto-suave">
        Últimas solicitudes de presupuesto recibidas (hasta 50, más recientes primero). Sólo
        lectura: el cierre de solicitudes aún no está disponible.
      </p>

      <div className="mt-6">
        {cargando ? (
          <FilasCargando />
        ) : error !== null ? (
          <FilasError
            titulo="No se han podido cargar las solicitudes"
            mensaje={error}
            onReintentar={reintentar}
          />
        ) : solicitudes.length === 0 ? (
          <FilasVacias
            titulo="Sin solicitudes"
            mensaje="Todavía no ha llegado ninguna solicitud de presupuesto."
          />
        ) : (
          <>
            <p className="text-sm text-texto-suave">
              {solicitudes.length === 1 ? '1 solicitud' : `${solicitudes.length} solicitudes`}.
            </p>
            <ul className="mt-4 flex list-none flex-col gap-4">
              {solicitudes.map((solicitud) => (
                <FilaSolicitud key={solicitud.id} solicitud={solicitud} />
              ))}
            </ul>
          </>
        )}
      </div>
    </section>
  );
}
