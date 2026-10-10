'use client';

import Link from 'next/link';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { Boton } from '@/components/ui/boton';
import { Campo } from '@/components/ui/campo';
import { apiForgotPassword } from '@/features/auth/api';
import {
  AVISO_ERROR,
  MENSAJE_RATE_LIMIT_RECUPERACION,
  MENSAJE_RECUPERACION_ENVIADA,
  mensajeDeError,
} from '@/features/auth/mensajes';
import { validarEmail } from '@/features/auth/validacion';
import { esApiError } from '@/lib/api';

/**
 * Paso 1 del reseteo (#57): pide el enlace por email. El backend responde 202
 * exista o no la cuenta, así que la confirmación es SIEMPRE la misma
 * (no-enumeración); sólo un 429 se muestra aparte.
 */
export function FormularioRecuperar() {
  const [errorEmail, setErrorEmail] = useState<string | null>(null);
  const [errorApi, setErrorApi] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [enviado, setEnviado] = useState(false);

  async function enviar(evento: FormEvent<HTMLFormElement>): Promise<void> {
    evento.preventDefault();
    const email = String(new FormData(evento.currentTarget).get('email') ?? '').trim();

    const errorDeValidacion = validarEmail(email);
    if (errorDeValidacion !== undefined) {
      setErrorEmail(errorDeValidacion);
      return;
    }
    setErrorEmail(null);
    setErrorApi(null);
    setEnviando(true);
    try {
      await apiForgotPassword({ email });
      setEnviado(true);
    } catch (causa) {
      setErrorApi(
        esApiError(causa) && causa.status === 429
          ? MENSAJE_RATE_LIMIT_RECUPERACION
          : mensajeDeError(causa),
      );
    } finally {
      setEnviando(false);
    }
  }

  if (enviado) {
    return (
      <div
        role="status"
        aria-live="polite"
        className="flex w-full max-w-md flex-col gap-4 rounded-lg border border-borde bg-superficie p-6 sm:p-8"
      >
        <h1 className="font-display text-2xl text-texto">Revisa tu email</h1>
        <p className="text-texto">{MENSAJE_RECUPERACION_ENVIADA}</p>
        <Link
          href="/login"
          className="rounded-md bg-acento-nogal px-4 py-2 text-center text-sm font-medium text-superficie transition-colors outline-none focus:ring-2 focus:ring-acento-nogal hover:bg-acento-hover"
        >
          Volver a login
        </Link>
      </div>
    );
  }

  return (
    <section className="w-full max-w-md rounded-lg border border-borde bg-superficie p-6 sm:p-8">
      <h1 className="font-display text-2xl text-texto">Recuperar contraseña</h1>
      <p className="mt-1 text-sm text-texto-suave">
        Escribe el email de tu cuenta y te enviaremos un enlace para elegir una contraseña nueva.
      </p>
      {/* noValidate: la validación accesible (aria-describedby + role="alert")
          es la nuestra; required/type se mantienen para lectores de pantalla. */}
      <form
        onSubmit={(evento) => void enviar(evento)}
        noValidate
        className="mt-6 flex flex-col gap-4"
      >
        <Campo
          id="recuperar-email"
          name="email"
          etiqueta="Email"
          type="email"
          autoComplete="email"
          error={errorEmail ?? undefined}
          required
        />

        {errorApi !== null ? (
          <p role="alert" aria-live="assertive" className={AVISO_ERROR}>
            {errorApi}
          </p>
        ) : null}

        <Boton type="submit" disabled={enviando}>
          {enviando ? 'Enviando…' : 'Enviar enlace'}
        </Boton>

        <p className="text-sm text-texto-suave">
          ¿Lo recuerdas?{' '}
          <Link
            href="/login"
            className="rounded-md text-acento-nogal underline underline-offset-4 outline-none focus:ring-2 focus:ring-acento-nogal"
          >
            Volver a login
          </Link>
        </p>
      </form>
    </section>
  );
}
