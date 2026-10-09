import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AdminLayout from '@/app/admin/layout';
import { SesionContext, type ValorSesion } from '@/features/auth/session';
import type { Usuario } from '@/lib/tipos';

const ruta = vi.hoisted(() => ({ actual: '/admin' }));

vi.mock('next/navigation', () => ({
  usePathname: () => ruta.actual,
}));

vi.mock('next/link', () => ({
  default: ({ href, children, ...rest }: { href: string; children: ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

function usuario(rol: string): Usuario {
  return {
    id: 1,
    email: 'ana@manti.dev',
    nombre: 'Ana',
    rol,
    activo: true,
    createdAt: '2026-01-01T10:00:00Z',
  };
}

function sesion(rol: string | null, cargando = false): ValorSesion {
  return {
    usuario: rol === null ? null : usuario(rol),
    cargando,
    iniciarSesion: vi.fn(),
    cerrarSesion: vi.fn(),
  };
}

function renderLayout(valor: ValorSesion) {
  return render(
    <SesionContext.Provider value={valor}>
      <AdminLayout params={Promise.resolve({})}>
        <p>Contenido del panel</p>
      </AdminLayout>
    </SesionContext.Provider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  ruta.actual = '/admin';
});

describe('AdminLayout (guard del backoffice)', () => {
  it('mientras_carga_la_sesion_muestra_un_esqueleto_y_ni_el_cta_ni_el_contenido', () => {
    renderLayout(sesion(null, true));

    expect(screen.getByRole('status')).toHaveTextContent(
      'Comprobando permisos del backoffice…',
    );
    expect(screen.queryByRole('link', { name: 'Inicia sesión' })).not.toBeInTheDocument();
    expect(screen.queryByText('Contenido del panel')).not.toBeInTheDocument();
  });

  it('sin_sesion_muestra_la_cta_de_login_y_oculta_el_contenido', () => {
    renderLayout(sesion(null));

    const cta = screen.getByRole('link', { name: 'Inicia sesión' });
    expect(cta).toHaveAttribute('href', '/login');
    expect(screen.queryByText('Contenido del panel')).not.toBeInTheDocument();
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('con_rol_user_muestra_el_403_con_enlace_al_home_y_sin_contenido', () => {
    renderLayout(sesion('USER'));

    const denegado = screen.getByRole('alert');
    expect(denegado).toHaveTextContent('403');
    expect(denegado).toHaveTextContent('Acceso denegado');
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toHaveAttribute('href', '/');
    expect(screen.queryByText('Contenido del panel')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Inicia sesión' })).not.toBeInTheDocument();
  });

  it('con_rol_admin_muestra_la_navegacion_y_el_contenido', () => {
    ruta.actual = '/admin/profesionales';
    renderLayout(sesion('ADMIN'));

    expect(screen.getByText('Contenido del panel')).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Secciones del backoffice' });
    expect(nav).toHaveTextContent('Profesionales');
    expect(nav).toHaveTextContent('Solicitudes');
    expect(screen.getByRole('link', { name: 'Profesionales' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByRole('link', { name: 'Resumen' })).not.toHaveAttribute('aria-current');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
