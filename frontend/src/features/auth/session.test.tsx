import { act, render, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiLogout, apiMe } from '@/features/auth/api';
import { ProveedorSesion, useSesion } from '@/features/auth/session';
import type { ValorSesion } from '@/features/auth/session';
import { bootstrapSession, limpiarSesionLocal } from '@/lib/auth';
import type { AuthResponse, Usuario } from '@/features/auth/types';

vi.mock('@/lib/auth', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/auth')>();
  return { ...real, bootstrapSession: vi.fn() };
});

vi.mock('@/features/auth/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/features/auth/api')>();
  return { ...real, apiMe: vi.fn(), apiLogout: vi.fn() };
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

const USUARIO_RAPIDO: Usuario = { ...USUARIO, id: 2, email: 'lucia@example.com' };
const SESION_RAPIDA: AuthResponse = { accessToken: 'token-rapido', expiresIn: 900, user: USUARIO_RAPIDO };

type Captura = { valor?: ValorSesion };

function Sonda({ onValor }: { readonly onValor: (valor: ValorSesion) => void }) {
  onValor(useSesion());
  return null;
}

function valorCapturado(captura: Captura): ValorSesion {
  const valor = captura.valor;
  if (valor === undefined) {
    throw new Error('El proveedor no ha expuesto la sesión');
  }
  return valor;
}

function montar(captura: Captura): void {
  render(
    <ProveedorSesion>
      <Sonda
        onValor={(valor) => {
          captura.valor = valor;
        }}
      />
    </ProveedorSesion>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  // El token en memoria es estado de módulo: se limpia entre tests.
  limpiarSesionLocal();
});

describe('ProveedorSesion', () => {
  it('sin_sesion_previa_el_bootstrap_deja_un_usuario_nulo', async () => {
    vi.mocked(bootstrapSession).mockResolvedValue(null);
    const captura: Captura = {};

    montar(captura);

    expect(valorCapturado(captura).cargando).toBe(true);
    await waitFor(() => expect(valorCapturado(captura).cargando).toBe(false));
    expect(valorCapturado(captura).usuario).toBeNull();
    expect(apiMe).not.toHaveBeenCalled();
  });

  it('con_refresh_valido_recupera_el_usuario_con_me', async () => {
    vi.mocked(bootstrapSession).mockResolvedValue(SESION);
    vi.mocked(apiMe).mockResolvedValue(USUARIO);
    const captura: Captura = {};

    montar(captura);

    await waitFor(() => expect(valorCapturado(captura).usuario).toEqual(USUARIO));
    expect(apiMe).toHaveBeenCalledTimes(1);
    expect(valorCapturado(captura).cargando).toBe(false);
  });

  it('el_bootstrap_no_pisa_una_sesion_iniciada_mientras_tanto', async () => {
    let resolverBootstrap: (sesion: AuthResponse) => void = () => {};
    vi.mocked(bootstrapSession).mockReturnValue(
      new Promise<AuthResponse>((resolver) => {
        resolverBootstrap = resolver;
      }),
    );
    vi.mocked(apiMe).mockResolvedValue(USUARIO);
    const captura: Captura = {};

    montar(captura);

    // El usuario inicia sesión mientras el bootstrap sigue en vuelo…
    act(() => {
      valorCapturado(captura).iniciarSesion(SESION_RAPIDA);
    });
    expect(valorCapturado(captura).usuario).toBe(USUARIO_RAPIDO);

    // …y el bootstrap se resuelve después con otra sesión: no debe pisarla.
    resolverBootstrap(SESION);
    await waitFor(() => expect(valorCapturado(captura).cargando).toBe(false));
    expect(valorCapturado(captura).usuario).toBe(USUARIO_RAPIDO);
    expect(apiMe).not.toHaveBeenCalled();
  });

  it('cerrar_sesion_limpia_el_usuario_expuesto', async () => {
    vi.mocked(bootstrapSession).mockResolvedValue(SESION);
    vi.mocked(apiMe).mockResolvedValue(USUARIO);
    vi.mocked(apiLogout).mockResolvedValue(undefined);
    const captura: Captura = {};

    montar(captura);
    await waitFor(() => expect(valorCapturado(captura).usuario).toEqual(USUARIO));

    await act(async () => {
      await valorCapturado(captura).cerrarSesion();
    });

    expect(apiLogout).toHaveBeenCalledTimes(1);
    expect(valorCapturado(captura).usuario).toBeNull();
  });
});
