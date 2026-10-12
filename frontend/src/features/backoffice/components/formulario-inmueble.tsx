'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { AVISO_ERROR } from '@/components/ui/avisos';
import { Boton } from '@/components/ui/boton';
import { esApiError } from '@/lib/api';
import type { InmuebleDetalle } from '@/features/inmuebles/types';
import { actualizarInmueble, crearInmueble } from '../api';
import { MENSAJES, clasificarErrorEnvioInmueble } from '../mensajes';
import {
  CAMPOS_INMUEBLE,
  construirEnvio,
  hayErroresInmueble,
  validarInmueble,
} from '../validacion-inmueble';
import type { ErroresInmueble } from '../validacion-inmueble';
import {
  CamposAnuncio,
  CamposCaracteristicas,
  CamposUbicacion,
  leerDatosFormulario,
  valoresDesdeDetalle,
} from './campos-inmueble';

type FormularioInmuebleProps = Readonly<{
  /** Inmueble a editar; `null` ⇒ alta (POST). */
  inicial: InmuebleDetalle | null;
}>;

const CLASE_SECCION = 'flex flex-col gap-4';
const CLASE_TITULO_SECCION = 'font-display text-xl text-texto border-b border-borde pb-2';

/**
 * Alta (POST) y edición (PUT) de inmuebles. Los inputs son no controlados
 * (defaultValue desde `inicial`); la validación client espeja el backend y
 * un 400 con `errors[]` pinta el mensaje EN el campo correspondiente.
 */
export function FormularioInmueble({ inicial }: FormularioInmuebleProps) {
  const router = useRouter();
  const [errores, setErrores] = useState<ErroresInmueble>({});
  const [errorApi, setErrorApi] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  /** Precarga en edición; `undefined` en alta (inputs vacíos). */
  const valores = inicial === null ? undefined : valoresDesdeDetalle(inicial);

  async function enviar(evento: FormEvent<HTMLFormElement>): Promise<void> {
    // Guard de reentrada: un segundo submit mientras la petición está en
    // vuelo se descarta (el botón ya está deshabilitado, pero Enter no).
    if (enviando) {
      evento.preventDefault();
      return;
    }
    evento.preventDefault();
    const campos = leerDatosFormulario(new FormData(evento.currentTarget));

    const erroresCliente = validarInmueble(campos);
    setErrores(erroresCliente);
    setErrorApi(null);
    if (hayErroresInmueble(erroresCliente)) return;

    const payload = construirEnvio(campos);
    setEnviando(true);
    try {
      if (inicial === null) {
        await crearInmueble(payload);
      } else {
        await actualizarInmueble(inicial.id, payload);
      }
      // Éxito ⇒ vuelve al listado (donde el effect recarga la página).
      router.push('/admin/inmuebles');
    } catch (causa: unknown) {
      // 400 con `errors[]` ⇒ error EN el campo; 401/403/404/409 y el resto
      // quedan en el aviso global (el 409 traduce el soft-delete).
      const fallo = clasificarErrorEnvioInmueble(causa, CAMPOS_INMUEBLE, MENSAJES.guardarInmueble);
      setErrores((prev): ErroresInmueble => ({ ...prev, ...fallo.campos }));
      setErrorApi(fallo.global);
      // 401/403: la sesión ya no sirve o no es ADMIN; el guard redirige al
      // detectar la pérdida, pero el aviso explica qué ha pasado.
      if (esApiError(causa) && (causa.status === 401 || causa.status === 403)) {
        setErrorApi(
          causa.status === 401
            ? 'Tu sesión ha caducado; vuelve a iniciar sesión como administrador.'
            : 'Tu cuenta no tiene permisos de administrador.',
        );
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form
      onSubmit={(evento) => void enviar(evento)}
      noValidate
      className="flex flex-col gap-6 rounded-lg border border-borde bg-superficie p-5 sm:p-6"
    >
      <fieldset className={CLASE_SECCION}>
        <legend className="sr-only">Anuncio</legend>
        <h2 className={CLASE_TITULO_SECCION}>Anuncio</h2>
        <CamposAnuncio errores={errores} valores={valores} />
      </fieldset>

      <fieldset className={CLASE_SECCION}>
        <legend className="sr-only">Características</legend>
        <h2 className={CLASE_TITULO_SECCION}>Características</h2>
        <CamposCaracteristicas errores={errores} valores={valores} />
      </fieldset>

      <fieldset className={CLASE_SECCION}>
        <legend className="sr-only">Ubicación</legend>
        <h2 className={CLASE_TITULO_SECCION}>Ubicación</h2>
        <CamposUbicacion errores={errores} valores={valores} />
      </fieldset>

      {inicial !== null && inicial.codigoIne !== null ? (
        <p className="text-sm text-texto-suave">
          Código INE del municipio: <strong className="text-texto">{inicial.codigoIne}</strong> (se
          usa para los precios oficiales del MIVAU; se rellena por importación, no por el
          formulario).
        </p>
      ) : null}

      {errorApi !== null ? (
        <p role="alert" aria-live="assertive" className={AVISO_ERROR}>
          {errorApi}
        </p>
      ) : null}

      <div className="flex flex-wrap gap-3 border-t border-borde pt-4">
        <Boton type="submit" disabled={enviando}>
          {enviando
            ? 'Guardando…'
            : inicial === null
              ? 'Crear inmueble'
              : 'Guardar cambios'}
        </Boton>
        <Boton variante="secundario" onClick={() => router.push('/admin/inmuebles')}>
          Cancelar
        </Boton>
      </div>
    </form>
  );
}
