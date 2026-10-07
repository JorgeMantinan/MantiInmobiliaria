import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SesionContext, type ValorSesion } from '@/features/auth/session';
import { ListadoFavoritos } from '@/features/swipes/components/listado-favoritos';
import type { PaginaSwipes } from '@/features/swipes/types';
import type { InmuebleResumen } from '@/features/inmuebles/types';
import { apiFetch } from '@/lib/api';
import type { Usuario } from '@/lib/tipos';

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

const USUARIO: Usuario = {
  id: 1,
  email: 'ana@manti.dev',
  nombre: 'Ana',
  rol: 'USER',
  activo: true,
  createdAt: '2026-01-01T10:00:00Z',
};

const CON_SESION: ValorSesion = {
  usuario: USUARIO,
  cargando: false,
  iniciarSesion: vi.fn(),
  cerrarSesion: vi.fn(),
};

const SIN_SESION: ValorSesion = {
  usuario: null,
  cargando: false,
  iniciarSesion: vi.fn(),
  cerrarSesion: vi.fn(),
};

function vivienda(id: number, titulo: string): InmuebleResumen {
  return {
    id,
    titulo,
    precio: 200000,
    tipo: 'PISO',
    estado: 'VENTA',
    superficieM2: 96,
    habitaciones: 3,
    banios: 1,
    municipio: 'Madrid',
    provincia: 'Madrid',
    imagenUrl: null,
    destacado: false,
    lat: 40.4168,
    lng: -3.7038,
    createdAt: '2026-01-01T10:00:00Z',
  };
}

function pagina(content: readonly InmuebleResumen[], nextCursor: string | null): PaginaSwipes {
  return { content, nextCursor };
}

/** Favoritos por defecto: cualquier GET devuelve la página `pedida`. */
function instalarFavoritos(pedida: PaginaSwipes): void {
  vi.mocked(apiFetch).mockImplementation(async (_ruta, opciones) => {
    if (opciones?.method === 'DELETE') return undefined;
    return pedida;
  });
}

function renderConSesion(valor: ValorSesion, ui: ReactNode) {
  return render(<SesionContext.Provider value={valor}>{ui}</SesionContext.Provider>);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ListadoFavoritos', () => {
  it('lista_los_favoritos_con_su_boton_de_quitar', async () => {
    instalarFavoritos(pagina([vivienda(1, 'Piso Uno'), vivienda(2, 'Piso Dos')], null));

    renderConSesion(CON_SESION, <ListadoFavoritos />);

    expect(await screen.findByText('Mostrando 2 favoritos')).toBeInTheDocument();
    expect(screen.getByText('Piso Uno')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Quitar de favoritos/ })).toHaveLength(2);
  });

  it('al_quitar_se_llama_al_delete_y_la_tarjeta_desaparece', async () => {
    const usuario = userEvent.setup();
    instalarFavoritos(pagina([vivienda(1, 'Piso Uno'), vivienda(2, 'Piso Dos')], null));

    renderConSesion(CON_SESION, <ListadoFavoritos />);
    await screen.findByText('Mostrando 2 favoritos');

    await usuario.click(screen.getByRole('button', { name: 'Quitar de favoritos: Piso Uno' }));

    expect(await screen.findByText('Mostrando 1 favorito')).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/swipes/favoritos/1', { method: 'DELETE' });
    expect(screen.queryByText('Piso Uno')).not.toBeInTheDocument();
  });

  it('un_fallo_del_delete_conserva_el_favorito_y_anuncia_el_error', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiFetch).mockImplementation(async (_ruta, opciones) => {
      if (opciones?.method === 'DELETE') {
        throw new Error('no se ha podido quitar');
      }
      return pagina([vivienda(1, 'Piso Uno')], null);
    });

    renderConSesion(CON_SESION, <ListadoFavoritos />);
    await screen.findByText('Mostrando 1 favorito');

    await usuario.click(screen.getByRole('button', { name: 'Quitar de favoritos: Piso Uno' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('no se ha podido quitar');
    expect(screen.getByText('Piso Uno')).toBeInTheDocument();
  });

  it('cargar_mas_usa_el_cursor_y_acumula_sin_repetir', async () => {
    const usuario = userEvent.setup();
    const primera = pagina([vivienda(1, 'Piso Uno'), vivienda(2, 'Piso Dos')], 'c1');
    const segunda = pagina([vivienda(2, 'Piso Dos'), vivienda(3, 'Piso Tres')], null);
    vi.mocked(apiFetch).mockImplementation(async (ruta) => {
      if (ruta.includes('cursor=c1')) return segunda;
      return primera;
    });

    renderConSesion(CON_SESION, <ListadoFavoritos />);
    await screen.findByText('Mostrando 2 favoritos');

    await usuario.click(screen.getByRole('button', { name: 'Cargar más' }));

    expect(await screen.findByText('Mostrando 3 favoritos')).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/swipes/favoritos?limit=20&cursor=c1');
    expect(screen.getAllByText('Piso Dos')).toHaveLength(1);
    expect(screen.getByText('Piso Tres')).toBeInTheDocument();
  });

  it('muestra_el_error_de_carga_con_un_reintento', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('servidor de favoritos caído'));

    renderConSesion(CON_SESION, <ListadoFavoritos />);

    expect(await screen.findByRole('alert')).toHaveTextContent('servidor de favoritos caído');
    instalarFavoritos(pagina([vivienda(1, 'Piso Uno')], null));
    await usuario.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Piso Uno')).toBeInTheDocument();
  });

  it('sin_favoritos_muestra_el_estado_vacio', async () => {
    instalarFavoritos(pagina([], null));

    renderConSesion(CON_SESION, <ListadoFavoritos />);

    expect(await screen.findByText('Aún no tienes favoritos')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Empezar a swipear' })).toHaveAttribute(
      'href',
      '/swipe',
    );
    expect(screen.queryByRole('button', { name: /Quitar de favoritos/ })).not.toBeInTheDocument();
  });

  it('con_sesion_anonima_muestra_la_cta_sin_llamar_a_la_api', async () => {
    renderConSesion(SIN_SESION, <ListadoFavoritos />);

    expect(await screen.findByText('Inicia sesión para guardar tus favoritos')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).toHaveAttribute('href', '/login');
    expect(apiFetch).not.toHaveBeenCalled();
  });
});
