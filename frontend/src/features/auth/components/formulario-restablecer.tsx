'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { Boton } from '@/components/ui/boton';
import { Campo } from '@/components/ui/campo';
import { apiResetPassword } from '@/features/auth/api';
import {
  AVISO_ERROR,
  MENSAJE_ENLACE_INVALIDO,
  MENSAJE_RATE_LIMIT_RECUPERACION,
  esTokenInvalido,
  mensajeDeError,
} from '@/features/auth/mensajes';
import {
  PASSWORD_MAXIMO,
  PASSWORD_MINIMO,
  hayErrores,
  validarRestablecer,
} from '@/features/auth/validacion';
import type { ErroresRestablecer } from '@/features/auth/validacion';
import { esApiError } from '@/lib/api';

type FormularioRestablecerProps = Readonly<{
  /** Token opaco de `?token=` (lo lee el server component de la página). */
  token?: string;
}>;

type PantallaProps = Readonly<{ titulo: string; children: ReactNode }>;

/** Card fija (mismo estilo que la del formulario) para los estados sin form. */
function Pantalla({ titulo, children }: PantallaProps) {
  return (
    <div className="flex w-full max-w-md flex-col gap-4 rounded-lg border border-borde bg-superficie p-6 sm:p-8">
      <h1 className="font-display text-2xl text-texto">{titulo}</h1>
      {children}
    </div>
  );
}

/**
 * Paso 2 del reseteo (#57): canjea el token del email por la contraseña nueva.
 * El token es de un solo uso: se captura en el primer render y se limpia de la
 * URL con `history.replaceState` (un F5 no debe reintentarlo), igual que el
 * callback de Google.
 */
export function FormularioRestablecer({ token }: FormularioRestablecerProps) {
  // Se decide en el primer render: al limpiar la URL, el prop `token` puede
  // pasar a undefined y NO debe perderse el valor capturado (mismo patrón que
  // `CallbackGoogle` con su `code`).
  const [inicio] = useState(() => ({ sinToken: token === undefined, token }));
  const [errores, setErrores] = useState<ErroresRestablecer>({});
  const [errorApi, setErrorApi] = useState<string | null>(null);
  const [tokenInvalido, setTokenInvalido] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [restablecida, setRestablecida] = useState(false);

  // Limpia `?token=` al montar (antes de cualquier envío): el enlace es
  // secreto y de un solo uso. `history.replaceState` y no `router.replace`
  // para no re-disparar este effect.
  useEffect(() => {
    if (!inicio.sinToken) {
      window.history.replaceState(null, '', '/restablecer');
    }
  }, [inicio.sinToken]);

  async function enviar(evento: FormEvent<HTMLFormElement>): Promise<void> {
    evento.preventDefault();
    const datos = new FormData(evento.currentTarget);
    const password = String(datos.get('password') ?? '');
    const confirmacion = String(datos.get('confirmacion') ?? '');

    const erroresCliente = validarRestablecer(password, confirmacion);
    setErrores(erroresCliente);
    setErrorApi(null);
    if (hayErrores(erroresCliente)) {
      return;
    }

    setEnviando(true);
    try {
      await apiResetPassword({ token: inicio.token ?? '', password });
      setRestablecida(true);
    } catch (causa) {
      if (esTokenInvalido(causa)) {
        setTokenInvalido(true);
        setErrorApi(MENSAJE_ENLACE_INVALIDO);
      } else {
        setErrorApi(
          esApiError(causa) && causa.status === 429
            ? MENSAJE_RATE_LIMIT_RECUPERACION
            : mensajeDeError(causa),
        );
      }
    } finally {
      setEnviando(false);
    }
  }

  if (restablecida) {
    return (
      <Pantalla titulo="Contraseña actualizada">
        <p role="status" aria-live="polite" className="text-texto">
          Tu contraseña se ha cambiado correctamente. Ya puedes iniciar sesión con ella.
        </p>
        <Link
          href="/login"
          className="rounded-md bg-acento-nogal px-4 py-2 text-center text-sm font-medium text-superficie transition-colors outline-none focus:ring-2 focus:ring-acento-nogal hover:bg-acento-hover"
        >
          Ir a iniciar sesión
        </Link>
      </Pantalla>
    );
  }

  if (inicio.sinToken) {
    return (
      <Pantalla titulo="Enlace no válido">
        <p role="alert" aria-live="assertive" className={AVISO_ERROR}>
          {MENSAJE_ENLACE_INVALIDO}
        </p>
        <Link
          href="/recuperar"
          className="rounded-md bg-acento-nogal px-4 py-2 text-center text-sm font-medium text-superficie transition-colors outline-none focus:ring-2 focus:ring-acento-nogal hover:bg-acento-hover"
        >
          Solicitar un enlace nuevo
        </Link>
      </Pantalla>
    );
  }

  return (
    <section className="w-full max-w-md rounded-lg border border-borde bg-superficie p-6 sm:p-8">
      <h1 className="font-display text-2xl text-texto">Elige una contraseña nueva</h1>
      <p className="mt-1 text-sm text-texto-suave">
        Se cerrarán todas las sesiones abiertas con tu cuenta.
      </p>
      {/* noValidate: la validación accesible (aria-describedby + role="alert")
          es la nuestra; required/minLength siguen para lectores de pantalla. */}
      <form
        onSubmit={(evento) => void enviar(evento)}
        noValidate
        className="mt-6 flex flex-col gap-4"
      >
        <Campo
          id="restablecer-password"
          name="password"
          etiqueta="Nueva contraseña"
          type="password"
          autoComplete="new-password"
          minLength={PASSWORD_MINIMO}
          maxLength={PASSWORD_MAXIMO}
          error={errores.password}
          required
        />
        <Campo
          id="restablecer-confirmacion"
          name="confirmacion"
          etiqueta="Repite la contraseña"
          type="password"
          autoComplete="new-password"
          error={errores.confirmacion}
          required
        />

        <p className="text-xs text-texto-suave">
          Mínimo {PASSWORD_MINIMO} y máximo {PASSWORD_MAXIMO} caracteres.
        </p>

        {errorApi !== null ? (
          <p role="alert" aria-live="assertive" className={AVISO_ERROR}>
            {errorApi}
          </p>
        ) : null}

        {tokenInvalido ? (
          <p className="text-sm text-texto-suave">
            <Link
              href="/recuperar"
              className="rounded-md text-acento-nogal underline underline-offset-4 outline-none focus:ring-2 focus:ring-acento-nogal"
            >
              Solicitar un enlace nuevo
            </Link>
          </p>
        ) : null}

        <Boton type="submit" disabled={enviando}>
          {enviando ? 'Guardando…' : 'Guardar contraseña'}
        </Boton>
      </form>
    </section>
  );
}
