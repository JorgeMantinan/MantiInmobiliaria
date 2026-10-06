import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ApiError,
  apiFetch,
  apiLogin,
  apiRegister,
  bootstrapSession,
  haySesion,
  limpiarSesionLocal,
} from '@/lib/api';
import type { AuthResponse, Usuario } from '@/lib/api';

const USUARIO: Usuario = {
  id: 1,
  email: 'ana@example.com',
  nombre: 'Ana Pérez',
  rol: 'USER',
  activo: true,
  createdAt: '2026-01-01T10:00:00Z',
};

const SESION: AuthResponse = { accessToken: 'token-nuevo', expiresIn: 900, user: USUARIO };

const fetchMock = vi.fn<(ruta: string, init?: RequestInit) => Promise<Response>>();

function json(cuerpo: unknown, status = 200): Response {
  return new Response(JSON.stringify(cuerpo), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function problem(status: number, title: string, detail: string): Response {
  return new Response(JSON.stringify({ title, status, detail }), {
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

describe('apiFetch', () => {
  it('ante_un_401_refresca_la_sesion_y_reintenta_una_sola_vez', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(json(SESION))
      .mockResolvedValueOnce(json({ ok: true }));

    const datos = await apiFetch<{ ok: boolean }>('/api/v1/inmuebles/1');

    expect(datos).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(String(fetchMock.mock.calls[1][0])).toContain('/api/v1/auth/refresh');
    expect(cabeceras(1)['X-Requested-With']).toBe('XMLHttpRequest');
    expect(cabeceras(1)).not.toHaveProperty('Authorization');
    expect(cabeceras(2).Authorization).toBe('Bearer token-nuevo');
    expect(fetchMock.mock.calls[2][1]?.credentials).toBe('include');
  });

  it('si_el_refresh_falla_lanza_error_de_sesion_caducada_y_limpia_el_token', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(problem(401, 'no-autenticado', 'Sesión inválida o caducada'));

    const promesa = apiFetch('/api/v1/inmuebles');

    await expect(promesa).rejects.toMatchObject({
      name: 'ApiError',
      status: 401,
      title: 'Sesión caducada',
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(haySesion()).toBe(false);
  });

  it('un_segundo_401_tras_refrescar_no_vuelve_a_refrescar', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(json(SESION))
      .mockResolvedValueOnce(new Response(null, { status: 401 }));

    await expect(apiFetch('/api/v1/inmuebles')).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('un_401_en_el_login_no_dispara_el_refresh', async () => {
    fetchMock.mockResolvedValueOnce(problem(401, 'no-autenticado', 'Credenciales incorrectas'));

    await expect(apiLogin({ email: 'ana@example.com', password: 'mala1234' })).rejects.toMatchObject({
      status: 401,
      detail: 'Credenciales incorrectas',
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('parsea_el_problemdetail_como_ApiError_con_title_y_detail', async () => {
    fetchMock.mockResolvedValueOnce(
      problem(409, 'conflicto', 'El email ya está registrado'),
    );

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

describe('bootstrapSession', () => {
  it('devuelve_la_sesion_cuando_la_cookie_de_refresh_es_valida', async () => {
    fetchMock.mockResolvedValueOnce(json(SESION));

    await expect(bootstrapSession()).resolves.toEqual(SESION);
    expect(haySesion()).toBe(true);
    expect(cabeceras(0)['X-Requested-With']).toBe('XMLHttpRequest');
  });

  it('devuelve_null_cuando_no_hay_sesion', async () => {
    fetchMock.mockResolvedValueOnce(problem(401, 'no-autenticado', 'Sesión inválida o caducada'));

    await expect(bootstrapSession()).resolves.toBeNull();
    expect(haySesion()).toBe(false);
  });
});
