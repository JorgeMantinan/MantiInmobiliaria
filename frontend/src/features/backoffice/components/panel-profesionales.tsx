'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { AVISO_ERROR } from '@/components/ui/avisos';
import { etiquetaEstado } from '../formato';
import { useProfesionalesAdmin } from '../hooks/use-profesionales-admin';
import { ESTADOS_PROFESIONAL, esEstadoProfesional } from '../types';
import type { EstadoProfesional } from '../types';
import { FilasCargando, FilasError, FilasVacias } from './estados-listado';
import { FilaProfesional } from './fila-profesional';

const CLASE_PESTANA =
  'rounded-md border px-3 py-1.5 text-sm transition-colors outline-none focus:ring-2 focus:ring-acento-nogal';

/** Pestaña por defecto cuando la URL no trae `?estado=` (o trae uno inválido). */
function estadoDesdeUrl(valor: string | null): EstadoProfesional {
  return esEstadoProfesional(valor) ? valor : 'PENDIENTE';
}

/**
 * Moderación de profesionales: el filtro va en la URL (`?estado=`) para que
 * la pestaña sea enlazable; la carga y el cambio optimista viven en el hook.
 */
export function PanelProfesionales() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const estado = estadoDesdeUrl(searchParams.get('estado'));
  const { cargando, profesionales, error, aviso, errorAccion, enVuelo, moderar, reintentar } =
    useProfesionalesAdmin(estado);

  function cambiarPestana(nuevo: EstadoProfesional): void {
    router.replace(`/admin/profesionales?estado=${nuevo}`, { scroll: false });
  }

  return (
    <section aria-labelledby="titulo-profesionales">
      <h1 id="titulo-profesionales" className="font-display text-3xl text-texto">
        Profesionales
      </h1>
      <p className="mt-2 text-texto-suave">
        Revisa las altas pendientes: al aprobar, el profesional empieza a recibir solicitudes.
      </p>

      <div role="group" aria-label="Filtrar por estado" className="mt-6 flex flex-wrap gap-2">
        {ESTADOS_PROFESIONAL.map((opcion) => (
          <button
            key={opcion}
            type="button"
            aria-pressed={opcion === estado}
            onClick={() => cambiarPestana(opcion)}
            className={`${CLASE_PESTANA} ${
              opcion === estado
                ? 'border-acento-nogal bg-superficie-alta font-medium text-texto'
                : 'border-borde bg-superficie text-texto-suave hover:bg-superficie-alta'
            }`}
          >
            {etiquetaEstado(opcion)}
          </button>
        ))}
      </div>

      {/* Regiones vivas del resultado de cada moderación. */}
      <div role="status" aria-live="polite" className={aviso === null ? 'sr-only' : 'mt-4 text-sm text-acento-hover'}>
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
            titulo="No se han podido cargar los profesionales"
            mensaje={error}
            onReintentar={reintentar}
          />
        ) : profesionales.length === 0 ? (
          <FilasVacias
            titulo="Sin profesionales en esta pestaña"
            mensaje={`No hay profesionales con estado ${etiquetaEstado(estado).toLowerCase()}.`}
          />
        ) : (
          <>
            <p className="text-sm text-texto-suave">
              {profesionales.length === 1
                ? '1 profesional'
                : `${profesionales.length} profesionales`}{' '}
              con estado {etiquetaEstado(estado).toLowerCase()}.
            </p>
            <ul className="mt-4 flex list-none flex-col gap-4">
              {profesionales.map((profesional) => (
                <FilaProfesional
                  key={profesional.id}
                  profesional={profesional}
                  ocupado={enVuelo.includes(profesional.id)}
                  onCambio={moderar}
                />
              ))}
            </ul>
          </>
        )}
      </div>
    </section>
  );
}
