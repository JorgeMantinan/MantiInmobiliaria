import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiLogin } from '@/features/auth/api';
import { LoginForm } from '@/features/auth/components/login-form';
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
  return { ...real, apiLogin: vi.fn() };
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

function renderizarLogin(valor: ValorSesion = valorDeSesion()) {
  const utils = render(
    <SesionContext.Provider value={valor}>
      <LoginForm />
    </SesionContext.Provider>,
  );
  return { ...utils, valor };
}

async function rellenarYEnviar(
  usuario: ReturnType<typeof userEvent.setup>,
  email: string,
  password: string,
): Promise<void> {
  await usuario.type(screen.getByLabelText('Email'), email);
  await usuario.type(screen.getByLabelText('Contraseña'), password);
  await usuario.click(screen.getByRole('button', { name: 'Iniciar sesión' }));
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('LoginForm', () => {
  it('al_enviar_llama_a_apiLogin_inicia_sesion_y_navega_al_inicio', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiLogin).mockResolvedValue(SESION);
    const { valor } = renderizarLogin();

    await rellenarYEnviar(usuario, 'ana@example.com', 'secreto123');

    await waitFor(() => {
      expect(apiLogin).toHaveBeenCalledWith({
        email: 'ana@example.com',
        password: 'secreto123',
      });
    });
    expect(valor.iniciarSesion).toHaveBeenCalledWith(SESION);
    expect(navegacion.push).toHaveBeenCalledWith('/');
  });

  it('muestra_el_detalle_del_problemdetail_cuando_las_credenciales_son_invalidas', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiLogin).mockRejectedValue(
      new ApiError(401, 'no-autenticado', 'Credenciales incorrectas'),
    );
    const { valor } = renderizarLogin();

    await rellenarYEnviar(usuario, 'ana@example.com', 'mala1234');

    expect(await screen.findByRole('alert')).toHaveTextContent('Credenciales incorrectas');
    expect(valor.iniciarSesion).not.toHaveBeenCalled();
    expect(navegacion.push).not.toHaveBeenCalled();
  });

  it('ante_un_429_muestra_un_mensaje_amigable', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiLogin).mockRejectedValue(
      new ApiError(429, 'rate-limit', 'Límite de peticiones alcanzado'),
    );
    const { valor } = renderizarLogin();

    await rellenarYEnviar(usuario, 'ana@example.com', 'secreto123');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Demasiados intentos. Espera un minuto y vuelve a intentarlo.',
    );
    expect(valor.iniciarSesion).not.toHaveBeenCalled();
    expect(navegacion.push).not.toHaveBeenCalled();
  });

  it('muestra_el_enlace_a_recuperar_la_contrasena', () => {
    renderizarLogin();

    expect(screen.getByRole('link', { name: '¿Olvidaste tu contraseña?' })).toHaveAttribute(
      'href',
      '/recuperar',
    );
  });
});
