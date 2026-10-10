'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import { AVISO_ERROR } from '@/components/ui/avisos';
import { Boton } from '@/components/ui/boton';
import { Campo } from '@/components/ui/campo';
import { esApiError } from '@/lib/api';
import { registrarProfesional } from '../api';
import { useGremios } from '../hooks/use-gremios';
import { MENSAJES, clasificarErrorEnvio } from '../mensajes';
import type { AltaProfesional } from '../types';
import {
  CAMPOS_ALTA,
  EMAIL_MAXIMO,
  MUNICIPIO_MAXIMO,
  NOMBRE_MAXIMO,
  TELEFONO_MAXIMO,
  hayErrores,
  validarAlta,
} from '../validacion';
import type { ErroresAlta } from '../validacion';
import { Confirmacion } from './confirmacion';
import { SelectorGremios } from './selector-gremios';

/** Alta de profesional: `POST /api/v1/profesionales/registro` (queda `PENDIENTE`). */
export function FormularioAlta() {
  const estado = useGremios();
  const [seleccion, setSeleccion] = useState<readonly string[]>([]);
  const [errores, setErrores] = useState<ErroresAlta>({});
  const [errorApi, setErrorApi] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  /** Nombres de los gremios enviados; `null` mientras no se confirme el alta. */
  const [registrada, setRegistrada] = useState<readonly string[] | null>(null);

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
      nombre: String(datos.get('nombre') ?? '').trim(),
      email: String(datos.get('email') ?? '').trim(),
      telefono: String(datos.get('telefono') ?? '').trim(),
      municipio: String(datos.get('municipio') ?? '').trim(),
      gremios: seleccion,
    };

    const erroresCliente = validarAlta(campos);
    setErrores(erroresCliente);
    setErrorApi(null);
    if (hayErrores(erroresCliente)) return;

    // `telefono` y `municipio` son opcionales: si van vacíos, no se envían.
    const alta: AltaProfesional = {
      nombre: campos.nombre,
      email: campos.email,
      gremios: campos.gremios,
      ...(campos.telefono === '' ? {} : { telefono: campos.telefono }),
      ...(campos.municipio === '' ? {} : { municipio: campos.municipio }),
    };

    setEnviando(true);
    try {
      await registrarProfesional(alta);
      setRegistrada(
        estado.gremios.filter((gremio) => seleccion.includes(gremio.slug)).map((g) => g.nombre),
      );
    } catch (causa) {
      if (esApiError(causa) && causa.status === 409) {
        setErrorApi(MENSAJES.emailRepetido);
      } else {
        // 400 con `errors[]` ⇒ error EN el campo; los campos desconocidos (y
        // el resto de fallos) se conservan en el aviso global, que puede
        // convivir con los de campo.
        const fallo = clasificarErrorEnvio(causa, CAMPOS_ALTA, MENSAJES.registro);
        setErrores((prev): ErroresAlta => ({ ...prev, ...fallo.campos }));
        setErrorApi(fallo.global);
      }
    } finally {
      setEnviando(false);
    }
  }

  if (registrada !== null) {
    return (
      <Confirmacion
        titulo={MENSAJES.registroRecibido}
        resumen={registrada}
        enlace={{ href: '/profesionales', texto: 'Volver a profesionales' }}
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
        id="alta-nombre"
        name="nombre"
        etiqueta="Nombre o razón social"
        type="text"
        autoComplete="organization"
        maxLength={NOMBRE_MAXIMO}
        error={errores.nombre}
        required
      />
      <Campo
        id="alta-email"
        name="email"
        etiqueta="Email"
        type="email"
        autoComplete="email"
        maxLength={EMAIL_MAXIMO}
        error={errores.email}
        required
      />
      <Campo
        id="alta-telefono"
        name="telefono"
        etiqueta="Teléfono (opcional)"
        type="tel"
        autoComplete="tel"
        maxLength={TELEFONO_MAXIMO}
        error={errores.telefono}
      />
      <Campo
        id="alta-municipio"
        name="municipio"
        etiqueta="Municipio (opcional)"
        type="text"
        autoComplete="address-level2"
        maxLength={MUNICIPIO_MAXIMO}
        error={errores.municipio}
      />

      <SelectorGremios
        id="alta-gremio"
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
        {enviando ? 'Enviando registro…' : 'Registrarme como profesional'}
      </Boton>
    </form>
  );
}
