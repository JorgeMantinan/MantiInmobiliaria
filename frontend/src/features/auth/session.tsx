'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import {
  apiLogout,
  apiMe,
  bootstrapSession,
  guardarSesion,
  observarPerdidaDeSesion,
} from '@/lib/api';
import type { AuthResponse, Usuario } from '@/lib/api';

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
    throw new Error('useSesion requiere un <SessionProvider> por encima');
  }
  return valor;
}

type SessionProviderProps = Readonly<{ children: ReactNode }>;

/**
 * Restaura la sesión al montar (refresh de la cookie + `/auth/me`) y expone
 * el usuario a toda la app. El access token vive en `lib/api.ts` (memoria).
 */
export function SessionProvider({ children }: SessionProviderProps) {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    let activo = true;

    async function restaurar(): Promise<void> {
      try {
        const restaurada = await bootstrapSession();
        if (!activo) return;
        if (restaurada === null) {
          setUsuario(null);
          return;
        }
        try {
          const yo = await apiMe();
          if (activo) setUsuario(yo);
        } catch {
          if (activo) setUsuario(restaurada.user);
        }
      } catch {
        if (activo) setUsuario(null);
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
    guardarSesion(sesion);
    setUsuario(sesion.user);
  }, []);

  const cerrarSesion = useCallback(async () => {
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
