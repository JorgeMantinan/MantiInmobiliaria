'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import { AVISO_ERROR } from '@/components/ui/avisos';
import { Boton } from '@/components/ui/boton';
import { Campo } from '@/components/ui/campo';
import { CampoTextoArea } from '@/components/ui/campo-textarea';
import { crearSolicitud } from '../api';
import { useGremios } from '../hooks/use-gremios';
import { MENSAJES, clasificarErrorEnvio } from '../mensajes';
import type { SolicitudNueva } from '../types';
import {
  CAMPOS_SOLICITUD,
  DESCRIPCION_MAXIMA,
  DESCRIPCION_MINIMA,
  EMAIL_MAXIMO,
  MUNICIPIO_MAXIMO,
  NOMBRE_MAXIMO,
  hayErrores,
  validarSolicitud,
} from '../validacion';
import type { ErroresSolicitud } from '../validacion';
import { Confirmacion } from './confirmacion';
import { SelectorGremios } from './selector-gremios';

/** Solicitud de presupuesto: catálogo de gremios + `POST /api/v1/solicitudes`. */
export function FormularioSolicitud() {
  const estado = useGremios();
  const [seleccion, setSeleccion] = useState<readonly string[]>([]);
  const [errores, setErrores] = useState<ErroresSolicitud>({});
  const [errorApi, setErrorApi] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  /** Nombres de los gremios enviados; `null` mientras no se confirme el envío. */
  const [enviada, setEnviada] = useState<readonly string[] | null>(null);

  async function enviar(evento: FormEvent<HTMLFormElement>): Promise<void> {
    // Guard de reentrada: el botón queda deshabilitado, pero un segundo submit
    // (p. ej. Enter repetido) mientras la petición sigue en vuelo se descarta.
    if (enviando) {
      evento.preventDefault();
      return;
    }
    evento.preventDefault();
    const datos = new FormData(evento.currentTarget);
    const campos = {
      nombreContacto: String(datos.get('nombreContacto') ?? '').trim(),
      emailContacto: String(datos.get('emailContacto') ?? '').trim(),
      telefono: String(datos.get('telefono') ?? '').trim(),
      municipio: String(datos.get('municipio') ?? '').trim(),
      descripcion: String(datos.get('descripcion') ?? '').trim(),
      gremios: seleccion,
    };

    const erroresCliente = validarSolicitud(campos);
    setErrores(erroresCliente);
    setErrorApi(null);
    if (hayErrores(erroresCliente)) return;

    // `telefono` es opcional en el contrato: si va vacío, no se envía la clave.
    const cuerpo = {
      nombreContacto: campos.nombreContacto,
      emailContacto: campos.emailContacto,
      municipio: campos.municipio,
      descripcion: campos.descripcion,
      gremios: campos.gremios,
    };
    const solicitud: SolicitudNueva =
      campos.telefono === '' ? cuerpo : { ...cuerpo, telefono: campos.telefono };

    setEnviando(true);
    try {
      await crearSolicitud(solicitud);
      setEnviada(
        estado.gremios.filter((gremio) => seleccion.includes(gremio.slug)).map((g) => g.nombre),
      );
    } catch (causa) {
      // 400 con `errors[]` ⇒ error EN el campo; el aviso global es fallback.
      const fallo = clasificarErrorEnvio(causa, CAMPOS_SOLICITUD, MENSAJES.solicitud);
      if (fallo.global === null) {
        setErrores((prev): ErroresSolicitud => ({ ...prev, ...fallo.campos }));
      } else {
        setErrorApi(fallo.global);
      }
    } finally {
      setEnviando(false);
    }
  }

  if (enviada !== null) {
    return (
      <Confirmacion
        titulo={MENSAJES.solicitudEnviada}
        resumen={enviada}
        enlace={{ href: '/', texto: 'Volver al inicio' }}
      />
    );
  }

  return (
    <form
      onSubmit={(evento) => void enviar(evento)}
      noValidate
      className="flex flex-col gap-4 rounded-lg border border-borde bg-superficie p-5 sm:p-6"
    >
      <Campo
        id="solicitud-nombre"
        name="nombreContacto"
        etiqueta="Tu nombre"
        type="text"
        autoComplete="name"
        maxLength={NOMBRE_MAXIMO}
        error={errores.nombreContacto}
        required
      />
      <Campo
        id="solicitud-email"
        name="emailContacto"
        etiqueta="Email de contacto"
        type="email"
        autoComplete="email"
        maxLength={EMAIL_MAXIMO}
        error={errores.emailContacto}
        required
      />
      <Campo
        id="solicitud-telefono"
        name="telefono"
        etiqueta="Teléfono (opcional)"
        type="tel"
        autoComplete="tel"
        error={errores.telefono}
      />
      <Campo
        id="solicitud-municipio"
        name="municipio"
        etiqueta="Municipio"
        type="text"
        autoComplete="address-level2"
        maxLength={MUNICIPIO_MAXIMO}
        error={errores.municipio}
        required
      />
      <CampoTextoArea
        id="solicitud-descripcion"
        name="descripcion"
        etiqueta="Descripción del trabajo"
        rows={5}
        required
        maxLength={DESCRIPCION_MAXIMA}
        error={errores.descripcion}
        ayuda={`Entre ${DESCRIPCION_MINIMA} y ${DESCRIPCION_MAXIMA} caracteres.`}
      />

      <SelectorGremios
        id="solicitud-gremio"
        estado={estado}
        seleccion={seleccion}
        onCambio={setSeleccion}
        error={errores.gremios}
      />

      {errorApi !== null ? (
        <p role="alert" aria-live="assertive" className={AVISO_ERROR}>
          {errorApi}
        </p>
      ) : null}

      <Boton type="submit" disabled={enviando || estado.cargando}>
        {enviando ? 'Enviando solicitud…' : 'Enviar solicitud'}
      </Boton>
    </form>
  );
}
