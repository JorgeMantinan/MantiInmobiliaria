import type { ReactNode } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SesionContext, type ValorSesion } from '@/features/auth/session';
import { ModoSwipe } from '@/features/swipes/components/modo-swipe';
import type { PaginaSwipes } from '@/features/swipes/types';
import type { InmuebleResumen } from '@/features/inmuebles/types';
import { ApiError, apiFetch } from '@/lib/api';
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

/**
 * Enruta `apiFetch`: las rutas del deck devuelven las páginas EN ORDEN (una
 * por llamada) y cualquier otra ruta (los POST de decisión) resuelve vacío.
 */
function instalarDeck(...paginas: readonly PaginaSwipes[]): void {
  let indice = 0;
  vi.mocked(apiFetch).mockImplementation(async (ruta: string) => {
    if (ruta.startsWith('/api/v1/swipes/deck')) {
      const pedida = paginas[Math.min(indice, paginas.length - 1)];
      indice += 1;
      return pedida;
    }
    return undefined;
  });
}

function renderConSesion(valor: ValorSesion, ui: ReactNode) {
  return render(<SesionContext.Provider value={valor}>{ui}</SesionContext.Provider>);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ModoSwipe', () => {
  it('carga_el_deck_y_anuncia_la_posicion_de_la_carta', async () => {
    instalarDeck(pagina([vivienda(1, 'Piso Uno'), vivienda(2, 'Piso Dos')], null));

    renderConSesion(CON_SESION, <ModoSwipe />);

    expect(await screen.findByText('Carta 1 de 2')).toBeInTheDocument();
    expect(screen.getByText('Piso Uno')).toBeInTheDocument();
    // Sólo la carta superior lleva contenido: las demás sólo asoman.
    expect(screen.queryByText('Piso Dos')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar como favorito' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Descartar' })).toBeInTheDocument();
  });

  it('al_pulsar_guardar_envia_un_like_y_avanza_el_deck', async () => {
    const usuario = userEvent.setup();
    instalarDeck(pagina([vivienda(1, 'Piso Uno'), vivienda(2, 'Piso Dos')], null));

    renderConSesion(CON_SESION, <ModoSwipe />);
    await screen.findByText('Carta 1 de 2');

    await usuario.click(screen.getByRole('button', { name: 'Guardar como favorito' }));

    expect(await screen.findByText('Carta 2 de 2')).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/swipes', {
      method: 'POST',
      body: { inmuebleId: 1, decision: 'LIKE' },
    });
    expect(await screen.findByText('Piso Dos')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('Piso Uno')).not.toBeInTheDocument());
  });

  it('al_pulsar_descartar_envia_un_nope', async () => {
    const usuario = userEvent.setup();
    instalarDeck(pagina([vivienda(1, 'Piso Uno'), vivienda(2, 'Piso Dos')], null));

    renderConSesion(CON_SESION, <ModoSwipe />);
    await screen.findByText('Carta 1 de 2');

    await usuario.click(screen.getByRole('button', { name: 'Descartar' }));

    expect(await screen.findByText('Carta 2 de 2')).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/swipes', {
      method: 'POST',
      body: { inmuebleId: 1, decision: 'NOPE' },
    });
  });

  it('agotado_el_deck_pide_la_siguiente_pagina_con_el_cursor', async () => {
    const usuario = userEvent.setup();
    instalarDeck(
      pagina([vivienda(1, 'Piso Uno'), vivienda(2, 'Piso Dos')], 'c1'),
      pagina([vivienda(3, 'Piso Tres')], null),
    );

    renderConSesion(CON_SESION, <ModoSwipe />);
    await screen.findByText('Carta 1 de 2');

    await usuario.click(screen.getByRole('button', { name: 'Descartar' }));
    await screen.findByText('Carta 2 de 2');
    await usuario.click(screen.getByRole('button', { name: 'Guardar como favorito' }));

    expect(await screen.findByText('Carta 3 de 3')).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/swipes/deck?limit=20&cursor=c1');
    expect(screen.getByText('Piso Tres')).toBeInTheDocument();
  });

  it('sin_cartas_ni_cursor_muestra_el_estado_de_fin_con_sus_enlaces', async () => {
    const usuario = userEvent.setup();
    instalarDeck(pagina([vivienda(1, 'Piso Uno')], null));

    renderConSesion(CON_SESION, <ModoSwipe />);
    await screen.findByText('Carta 1 de 1');

    await usuario.click(screen.getByRole('button', { name: 'Guardar como favorito' }));

    expect(await screen.findByText('¡No quedan viviendas por revisar!')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver favoritos' })).toHaveAttribute(
      'href',
      '/favoritos',
    );
    expect(screen.getByRole('link', { name: 'Explorar viviendas' })).toHaveAttribute(
      'href',
      '/inmuebles',
    );
    await waitFor(() => expect(screen.queryByRole('article')).not.toBeInTheDocument());
  });

  it('con_sesion_anonima_muestra_la_cta_sin_llamar_a_la_api', async () => {
    renderConSesion(SIN_SESION, <ModoSwipe />);

    expect(await screen.findByText('Inicia sesión para guardar tus favoritos')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).toHaveAttribute('href', '/login');
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it('un_401_en_el_deck_cae_a_la_cta_de_sesion', async () => {
    vi.mocked(apiFetch).mockRejectedValue(new ApiError(401, 'No autorizado', 'Sin sesión'));

    renderConSesion(CON_SESION, <ModoSwipe />);

    expect(await screen.findByText('Inicia sesión para guardar tus favoritos')).toBeInTheDocument();
    expect(screen.queryByRole('article')).not.toBeInTheDocument();
  });

  it('muestra_el_error_de_carga_con_un_reintento', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('el backend está caído'));

    renderConSesion(CON_SESION, <ModoSwipe />);

    expect(await screen.findByRole('alert')).toHaveTextContent('el backend está caído');
    instalarDeck(pagina([vivienda(1, 'Piso Uno')], null));
    await usuario.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Piso Uno')).toBeInTheDocument();
  });

  it('si_el_post_falla_la_carta_vuelve_al_deck_y_se_anuncia_el_error', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiFetch).mockImplementation(async (ruta: string) => {
      if (ruta === '/api/v1/swipes') {
        throw new Error('no se ha podido guardar');
      }
      return pagina([vivienda(1, 'Piso Uno'), vivienda(2, 'Piso Dos')], null);
    });

    renderConSesion(CON_SESION, <ModoSwipe />);
    await screen.findByText('Carta 1 de 2');

    await usuario.click(screen.getByRole('button', { name: 'Guardar como favorito' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('no se ha podido guardar');
    expect(await screen.findByText('Carta 1 de 2')).toBeInTheDocument();
    expect(screen.getByText('Piso Uno')).toBeInTheDocument();
  });
});
