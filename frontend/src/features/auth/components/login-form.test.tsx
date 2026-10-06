import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginForm } from '@/features/auth/components/login-form';
import { SesionContext } from '@/features/auth/session';
import type { ValorSesion } from '@/features/auth/session';
import { ApiError, apiLogin } from '@/lib/api';
import type { AuthResponse, Usuario } from '@/lib/api';

const navegacion = vi.hoisted(() => ({ push: vi.fn() }));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: navegacion.push, replace: vi.fn(), prefetch: vi.fn() }),
}));

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
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

function rellenarYEnviar(contenedor: HTMLElement): void {
  fireEvent.change(screen.getByLabelText('Email'), {
    target: { value: 'ana@example.com' },
  });
  fireEvent.change(screen.getByLabelText('Contraseña'), {
    target: { value: 'secreto123' },
  });
  const formulario = contenedor.querySelector('form');
  if (formulario === null) {
    throw new Error('No se encontró el formulario de login');
  }
  fireEvent.submit(formulario);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('LoginForm', () => {
  it('al_enviar_llama_a_apiLogin_inicia_sesion_y_navega_al_inicio', async () => {
    vi.mocked(apiLogin).mockResolvedValue(SESION);
    const { container, valor } = renderizarLogin();

    rellenarYEnviar(container);

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
    vi.mocked(apiLogin).mockRejectedValue(
      new ApiError(401, 'no-autenticado', 'Credenciales incorrectas'),
    );
    const { container, valor } = renderizarLogin();

    rellenarYEnviar(container);

    expect(await screen.findByRole('alert')).toHaveTextContent('Credenciales incorrectas');
    expect(valor.iniciarSesion).not.toHaveBeenCalled();
    expect(navegacion.push).not.toHaveBeenCalled();
  });

  it('ante_un_429_muestra_un_mensaje_amigable', async () => {
    vi.mocked(apiLogin).mockRejectedValue(
      new ApiError(429, 'rate-limit', 'Límite de peticiones alcanzado'),
    );
    const { container } = renderizarLogin();

    rellenarYEnviar(container);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Demasiados intentos. Espera un minuto y vuelve a intentarlo.',
    );
    expect(navegacion.push).not.toHaveBeenCalled();
  });
});
