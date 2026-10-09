'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { apiLogout, apiMe } from '@/features/auth/api';
import type { AuthResponse, Usuario } from '@/features/auth/types';
import {
  bootstrapSession,
  guardarSesion,
  observarPerdidaDeSesion,
} from '@/lib/auth';

export type ValorSesion = Readonly<{
  usuario: Usuario | null;
  cargando: boolean;
  iniciarSesion: (sesion: AuthResponse) => void;
  cerrarSesion: () => Promise<void>;
}>;

/** Contexto exportado para poder inyectar un valor de prueba en los tests. */
export const SesionContext = createContext<ValorSesion | null>(null);

export function useSesion(): ValorSesion {
  const valor = useContext(SesionContext);
  if (valor === null) {
    throw new Error('useSesion requiere un <ProveedorSesion> por encima');
  }
  return valor;
}

type ProveedorSesionProps = Readonly<{ children: ReactNode }>;

/**
 * Restaura la sesión al montar (refresh de la cookie + `/auth/me`) y expone
 * el usuario a toda la app. El access token vive en `lib/auth.ts` (memoria).
 */
export function ProveedorSesion({ children }: ProveedorSesionProps) {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [cargando, setCargando] = useState(true);
  // Carrera: si el login/canje resuelve mientras el bootstrap sigue volando,
  // el bootstrap NO debe pisar el estado que el usuario ya ve.
  const sesionEstablecida = useRef(false);

  useEffect(() => {
    let activo = true;

    async function restaurar(): Promise<void> {
      try {
        const restaurada = await bootstrapSession();
        if (!activo || sesionEstablecida.current) return;
        if (restaurada === null) {
          setUsuario(null);
          return;
        }
        try {
          const yo = await apiMe();
          if (activo && !sesionEstablecida.current) setUsuario(yo);
        } catch {
          if (activo && !sesionEstablecida.current) setUsuario(restaurada.user);
        }
      } catch {
        if (activo && !sesionEstablecida.current) setUsuario(null);
      } finally {
        if (activo) setCargando(false);
      }
    }

    void restaurar();
    const baja = observarPerdidaDeSesion(() => {
      setUsuario(null);
    });

    return () => {
      activo = false;
      baja();
    };
  }, []);

  const iniciarSesion = useCallback((sesion: AuthResponse) => {
    sesionEstablecida.current = true;
    guardarSesion(sesion);
    setUsuario(sesion.user);
  }, []);

  const cerrarSesion = useCallback(async () => {
    // También marca la carrera: un bootstrap en vuelo no debe "resucitar" la
    // sesión que el usuario acaba de cerrar.
    sesionEstablecida.current = true;
    try {
      await apiLogout();
    } catch {
      // El cierre nunca debe romper la UI: el token local ya se borra igual.
    } finally {
      setUsuario(null);
    }
  }, []);

  const valor = useMemo<ValorSesion>(
    () => ({ usuario, cargando, iniciarSesion, cerrarSesion }),
    [usuario, cargando, iniciarSesion, cerrarSesion],
  );

  return <SesionContext.Provider value={valor}>{children}</SesionContext.Provider>;
}
