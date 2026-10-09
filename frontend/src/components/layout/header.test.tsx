import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Header } from '@/components/layout/header';
import { SesionContext, type ValorSesion } from '@/features/auth/session';
import type { Usuario } from '@/lib/tipos';

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

const USUARIO: Usuario = {
  id: 1,
  email: 'ana@manti.dev',
  nombre: 'Ana',
  rol: 'USER',
  activo: true,
  createdAt: '2026-01-01T10:00:00Z',
};

function sesion(usuario: Usuario | null): ValorSesion {
  return { usuario, cargando: false, iniciarSesion: vi.fn(), cerrarSesion: vi.fn() };
}

function renderHeader(valor: ValorSesion) {
  return render(
    <SesionContext.Provider value={valor}>
      <Header />
    </SesionContext.Provider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Header', () => {
  it('con_sesion_muestra_los_enlaces_de_swipe_y_favoritos', () => {
    renderHeader(sesion(USUARIO));

    expect(screen.getByRole('link', { name: 'Swipe' })).toHaveAttribute('href', '/swipe');
    expect(screen.getByRole('link', { name: 'Favoritos' })).toHaveAttribute('href', '/favoritos');
    expect(screen.getByText('ana@manti.dev')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Salir' })).toBeInTheDocument();
  });

  it('sin_sesion_no_muestra_los_enlaces_privados', () => {
    renderHeader(sesion(null));

    expect(screen.queryByRole('link', { name: 'Swipe' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Favoritos' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Crear cuenta' })).toBeInTheDocument();
  });

  it('con_rol_admin_muestra_el_enlace_al_backoffice', () => {
    renderHeader(sesion({ ...USUARIO, rol: 'ADMIN' }));

    expect(screen.getByRole('link', { name: 'Admin' })).toHaveAttribute('href', '/admin');
  });

  it('con_rol_user_y_sin_sesion_no_muestra_el_enlace_de_administracion', () => {
    const { unmount } = renderHeader(sesion(USUARIO));
    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument();
    unmount();

    renderHeader(sesion(null));
    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument();
  });
});
