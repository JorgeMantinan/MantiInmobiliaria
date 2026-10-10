import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  apiForgotPassword,
  apiLogin,
  apiLogout,
  apiRegister,
  apiResetPassword,
} from '@/features/auth/api';
import type { AuthResponse, Usuario } from '@/features/auth/types';
import {
  guardarSesion,
  haySesion,
  limpiarSesionLocal,
  observarPerdidaDeSesion,
} from '@/lib/auth';

const USUARIO: Usuario = {
  id: 1,
  email: 'ana@example.com',
  nombre: 'Ana Pérez',
  rol: 'USER',
  activo: true,
  createdAt: '2026-01-01T10:00:00Z',
};

const fetchMock = vi.fn<(ruta: string, init?: RequestInit) => Promise<Response>>();

function problem(status: number, title: string, detail: string, type = ''): Response {
  return new Response(JSON.stringify({ type, title, status, detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

function cabeceras(peticion: number): Record<string, string> {
  return (fetchMock.mock.calls[peticion][1]?.headers ?? {}) as Record<string, string>;
}

beforeEach(() => {
  limpiarSesionLocal();
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('apiLogin', () => {
  it('un_401_en_el_login_no_dispara_el_refresh', async () => {
    fetchMock.mockResolvedValueOnce(problem(401, 'no-autenticado', 'Credenciales incorrectas'));

    await expect(
      apiLogin({ email: 'ana@example.com', password: 'mala1234' }),
    ).rejects.toMatchObject({
      status: 401,
      detail: 'Credenciales incorrectas',
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe('apiRegister', () => {
  it('parsea_el_problemdetail_como_ApiError_con_title_y_detail', async () => {
    fetchMock.mockResolvedValueOnce(problem(409, 'conflicto', 'El email ya está registrado'));

    await expect(
      apiRegister({ nombre: 'Ana', email: 'ana@example.com', password: 'secreto123' }),
    ).rejects.toEqual(
      expect.objectContaining({
        status: 409,
        title: 'conflicto',
        detail: 'El email ya está registrado',
      }),
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe('apiLogout', () => {
  it('notifica_a_los_observadores_antes_de_terminar', async () => {
    const sesion: AuthResponse = { accessToken: 'token-viejo', expiresIn: 900, user: USUARIO };
    guardarSesion(sesion);
    const observador = vi.fn();
    const baja = observarPerdidaDeSesion(observador);
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await apiLogout();

    expect(observador).toHaveBeenCalledTimes(1);
    expect(haySesion()).toBe(false);
    expect(cabeceras(0)['X-Requested-With']).toBe('XMLHttpRequest');
    baja();
  });
});

describe('apiForgotPassword', () => {
  it('envia_el_email_al_endpoint_forgot_y_acepta_el_202', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 202 }));

    await expect(apiForgotPassword({ email: 'ana@example.com' })).resolves.toBeUndefined();

    const [ruta, init] = fetchMock.mock.calls[0];
    expect(ruta).toContain('/api/v1/auth/password/forgot');
    expect(init?.method).toBe('POST');
    expect(JSON.parse(String(init?.body))).toEqual({ email: 'ana@example.com' });
  });

  it('un_429_se_propaga_como_ApiError', async () => {
    fetchMock.mockResolvedValueOnce(problem(429, 'rate-limit', 'Límite superado'));

    await expect(apiForgotPassword({ email: 'ana@example.com' })).rejects.toMatchObject({
      status: 429,
    });
  });
});

describe('apiResetPassword', () => {
  it('envia_token_y_password_al_endpoint_reset_y_acepta_el_204', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await expect(
      apiResetPassword({ token: 'token-opaco', password: 'secreto123' }),
    ).resolves.toBeUndefined();

    const [ruta, init] = fetchMock.mock.calls[0];
    expect(ruta).toContain('/api/v1/auth/password/reset');
    expect(init?.method).toBe('POST');
    expect(JSON.parse(String(init?.body))).toEqual({
      token: 'token-opaco',
      password: 'secreto123',
    });
  });

  it('el_400_token_invalido_se_parsea_con_el_type_del_problemdetail', async () => {
    fetchMock.mockResolvedValueOnce(
      problem(
        400,
        'Token inválido',
        'El enlace de restablecimiento no es válido, ha caducado o ya ha sido utilizado',
        'https://api.manti.dev/problems/token-invalido',
      ),
    );

    await expect(
      apiResetPassword({ token: 'caducado', password: 'secreto123' }),
    ).rejects.toMatchObject({
      status: 400,
      type: 'https://api.manti.dev/problems/token-invalido',
    });
  });
});
