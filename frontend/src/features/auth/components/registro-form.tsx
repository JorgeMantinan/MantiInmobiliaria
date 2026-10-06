'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { Boton } from '@/components/ui/boton';
import { Campo } from '@/components/ui/campo';
import { apiRegister } from '@/features/auth/api';
import { AVISO_ERROR, mensajeDeError } from '@/features/auth/mensajes';
import { useSesion } from '@/features/auth/session';
import type { ErroresRegistro } from '@/features/auth/validacion';
import {
  PASSWORD_MAXIMO,
  PASSWORD_MINIMO,
  hayErrores,
  validarRegistro,
} from '@/features/auth/validacion';
import { esApiError } from '@/lib/api';

const MENSAJE_EMAIL_REPETIDO = 'Ese email ya está registrado.';

/** Alta de cuenta: el backend devuelve tokens, así que queda auto-login. */
export function RegistroForm() {
  const router = useRouter();
  const { iniciarSesion } = useSesion();
  const [errores, setErrores] = useState<ErroresRegistro>({});
  const [errorApi, setErrorApi] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento: FormEvent<HTMLFormElement>): Promise<void> {
    evento.preventDefault();
    const datos = new FormData(evento.currentTarget);
    const nombre = String(datos.get('nombre') ?? '');
    const email = String(datos.get('email') ?? '');
    const password = String(datos.get('password') ?? '');
    const confirmacion = String(datos.get('confirmacion') ?? '');

    const erroresCliente = validarRegistro({ nombre, email, password, confirmacion });
    setErrores(erroresCliente);
    setErrorApi(null);
    if (hayErrores(erroresCliente)) {
      return;
    }

    setEnviando(true);
    try {
      const sesion = await apiRegister({ nombre, email, password });
      iniciarSesion(sesion);
      router.push('/');
    } catch (causa) {
      setErrorApi(
        esApiError(causa) && causa.status === 409
          ? MENSAJE_EMAIL_REPETIDO
          : mensajeDeError(causa),
      );
    } finally {
      setEnviando(false);
    }
  }

  return (
    // noValidate: mandan nuestras reglas (8-72, confirmación) con avisos
    // accesibles; required/minLength siguen presentes para lectores de pantalla.
    <form
      onSubmit={(evento) => void enviar(evento)}
      noValidate
      className="mt-6 flex flex-col gap-4"
    >
      <Campo
        id="registro-nombre"
        name="nombre"
        etiqueta="Nombre"
        type="text"
        autoComplete="name"
        error={errores.nombre}
        required
      />
      <Campo
        id="registro-email"
        name="email"
        etiqueta="Email"
        type="email"
        autoComplete="email"
        error={errores.email}
        required
      />
      <Campo
        id="registro-password"
        name="password"
        etiqueta="Contraseña"
        type="password"
        autoComplete="new-password"
        minLength={PASSWORD_MINIMO}
        maxLength={PASSWORD_MAXIMO}
        error={errores.password}
        required
      />
      <Campo
        id="registro-confirmacion"
        name="confirmacion"
        etiqueta="Repite la contraseña"
        type="password"
        autoComplete="new-password"
        error={errores.confirmacion}
        required
      />

      <p className="text-xs text-texto-suave">Mínimo {PASSWORD_MINIMO} caracteres.</p>

      {errorApi !== null ? (
        <p role="alert" aria-live="assertive" className={AVISO_ERROR}>
          {errorApi}
        </p>
      ) : null}

      <Boton type="submit" disabled={enviando}>
        {enviando ? 'Creando cuenta…' : 'Crear cuenta'}
      </Boton>

      <p className="text-sm text-texto-suave">
        ¿Ya tienes cuenta?{' '}
        <Link
          href="/login"
          className="rounded-md text-acento-nogal underline underline-offset-4 outline-none focus:ring-2 focus:ring-acento-nogal"
        >
          Iniciar sesión
        </Link>
      </p>
    </form>
  );
}
