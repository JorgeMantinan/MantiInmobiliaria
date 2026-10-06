import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CallbackGoogle } from '@/features/auth/components/callback-google';
import { SesionContext } from '@/features/auth/session';
import type { ValorSesion } from '@/features/auth/session';
import { ApiError, apiExchangeGoogleCode } from '@/lib/api';
import type { AuthResponse, Usuario } from '@/lib/api';

const navegacion = vi.hoisted(() => ({ replace: vi.fn() }));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: navegacion.replace, prefetch: vi.fn() }),
}));

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
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
) {
  const utils = render(
    <SesionContext.Provider value={valor}>
      <CallbackGoogle {...props} />
    </SesionContext.Provider>,
  );
  return { ...utils, valor };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('CallbackGoogle', () => {
  it('canjea_el_code_por_la_sesion_y_redirige_al_inicio', async () => {
    vi.mocked(apiExchangeGoogleCode).mockResolvedValue(SESION);
    const { valor } = renderizarCallback({ code: 'codigo-de-google' });

    await waitFor(() => {
      expect(apiExchangeGoogleCode).toHaveBeenCalledWith('codigo-de-google');
    });
    expect(valor.iniciarSesion).toHaveBeenCalledWith(SESION);
    await waitFor(() => {
      expect(navegacion.replace).toHaveBeenCalledWith('/');
    });
  });

  it('sin_code_muestra_error_y_el_enlace_a_login', () => {
    renderizarCallback({ error: 'access_denied' });

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Has cancelado el acceso con Google.',
    );
    expect(screen.getByRole('link', { name: 'Volver a iniciar sesión' })).toHaveAttribute(
      'href',
      '/login',
    );
    expect(apiExchangeGoogleCode).not.toHaveBeenCalled();
    expect(navegacion.replace).not.toHaveBeenCalled();
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
});
