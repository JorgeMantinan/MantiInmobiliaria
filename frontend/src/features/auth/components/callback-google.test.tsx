import { StrictMode } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiExchangeGoogleCode } from '@/features/auth/api';
import { CallbackGoogle } from '@/features/auth/components/callback-google';
import { SesionContext } from '@/features/auth/session';
import type { ValorSesion } from '@/features/auth/session';
import { ApiError } from '@/lib/api';
import type { AuthResponse, Usuario } from '@/features/auth/types';

const navegacion = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: navegacion.push, replace: navegacion.replace, prefetch: vi.fn() }),
}));

vi.mock('@/features/auth/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/features/auth/api')>();
  return { ...real, apiExchangeGoogleCode: vi.fn() };
});

const USUARIO: Usuario = {
  id: 1,
  email: 'ana@example.com',
  nombre: 'Ana Pérez',
  rol: 'USER',
  activo: true,
  createdAt: '2026-01-01T10:00:00Z',
};

const SESION: AuthResponse = { accessToken: 'token', expiresIn: 900, user: USUARIO };

function valorDeSesion(): ValorSesion {
  return {
    usuario: null,
    cargando: false,
    iniciarSesion: vi.fn(),
    cerrarSesion: vi.fn(async () => {}),
  };
}

function renderizarCallback(
  props: Readonly<{ code?: string; error?: string }>,
  valor: ValorSesion = valorDeSesion(),
  opciones: Readonly<{ estricto?: boolean }> = {},
) {
  const contenido = (
    <SesionContext.Provider value={valor}>
      <CallbackGoogle {...props} />
    </SesionContext.Provider>
  );
  const utils = render(opciones.estricto ? <StrictMode>{contenido}</StrictMode> : contenido);
  return { ...utils, valor };
}

beforeEach(() => {
  vi.clearAllMocks();
  window.history.replaceState(null, '', '/');
});

describe('CallbackGoogle', () => {
  it('canjea_el_code_limpia_la_url_y_redirige_al_inicio', async () => {
    window.history.replaceState(null, '', '/auth/google/callback?code=codigo-de-google');
    vi.mocked(apiExchangeGoogleCode).mockResolvedValue(SESION);
    const { valor } = renderizarCallback({ code: 'codigo-de-google' });

    await waitFor(() => {
      expect(apiExchangeGoogleCode).toHaveBeenCalledWith('codigo-de-google');
    });
    expect(valor.iniciarSesion).toHaveBeenCalledWith(SESION);
    await waitFor(() => {
      expect(navegacion.replace).toHaveBeenCalledWith('/');
    });
    // El código de un solo uso se limpia de la URL antes del canje.
    expect(window.location.pathname).toBe('/auth/google/callback');
    expect(window.location.search).toBe('');
  });

  it('sin_code_muestra_error_y_el_enlace_a_login_sin_navegar', () => {
    window.history.replaceState(null, '', '/auth/google/callback?error=access_denied');
    renderizarCallback({ error: 'access_denied' });

    expect(screen.getByRole('alert')).toHaveTextContent('Has cancelado el acceso con Google.');
    expect(screen.getByRole('link', { name: 'Volver a iniciar sesión' })).toHaveAttribute(
      'href',
      '/login',
    );
    expect(apiExchangeGoogleCode).not.toHaveBeenCalled();
    // No se navega a '/': el usuario tiene que llegar a leer el motivo.
    expect(navegacion.replace).not.toHaveBeenCalledWith('/');
    // La URL residual (`?error=`) se limpia con history.replaceState.
    expect(window.location.pathname).toBe('/auth/google/callback');
    expect(window.location.search).toBe('');
  });

  it('un_error_desconocido_no_rompe_el_render', () => {
    window.history.replaceState(null, '', '/auth/google/callback?error=constructor');
    renderizarCallback({ error: 'constructor' });

    expect(screen.getByRole('alert')).toHaveTextContent(
      'No se ha podido completar el acceso con Google.',
    );
    expect(navegacion.replace).not.toHaveBeenCalled();
    expect(window.location.search).toBe('');
  });

  it('con_el_code_caducado_muestra_el_error_de_la_api_y_el_enlace_a_login', async () => {
    vi.mocked(apiExchangeGoogleCode).mockRejectedValue(
      new ApiError(401, 'no-autenticado', 'Código de autenticación inválido o caducado'),
    );
    renderizarCallback({ code: 'codigo-caducado' });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Código de autenticación inválido o caducado',
    );
    expect(screen.getByRole('link', { name: 'Volver a iniciar sesión' })).toBeInTheDocument();
    expect(navegacion.replace).not.toHaveBeenCalled();
  });

  it('callback_no_canjea_dos_veces_aunque_strict_mode_remonte_el_effect', async () => {
    vi.mocked(apiExchangeGoogleCode).mockResolvedValue(SESION);
    const { valor } = renderizarCallback({ code: 'codigo-unico' }, valorDeSesion(), {
      estricto: true,
    });

    await waitFor(() => expect(navegacion.replace).toHaveBeenCalledWith('/'));
    expect(apiExchangeGoogleCode).toHaveBeenCalledTimes(1);
    expect(valor.iniciarSesion).toHaveBeenCalledTimes(1);
  });
});
