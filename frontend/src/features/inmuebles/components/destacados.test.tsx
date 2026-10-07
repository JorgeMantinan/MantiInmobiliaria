import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Destacados } from '@/features/inmuebles/components/destacados';
import type { InmuebleResumen } from '@/features/inmuebles/types';
import { ApiError, apiFetch } from '@/lib/api';

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

const DESTACADO: InmuebleResumen = {
  id: 12,
  titulo: 'Casa con jardín',
  precio: 385000,
  tipo: 'CASA',
  estado: 'VENTA',
  superficieM2: 140,
  habitaciones: 4,
  banios: 2,
  municipio: 'Pozuelo de Alarcón',
  provincia: 'Madrid',
  imagenUrl: null,
  destacado: true,
  lat: 40.435,
  lng: -3.81,
  createdAt: '2026-01-01T10:00:00Z',
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Destacados', () => {
  it('pide_la_primera_pagina_de_destacados_con_limit_6', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      content: [DESTACADO],
      nextCursor: null,
      totalElements: 1,
    });

    render(<Destacados />);

    expect(await screen.findByText('Casa con jardín')).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledWith('/inmuebles?destacado=true&limit=6');
    expect(screen.getByRole('link', { name: 'Ver todas las viviendas' })).toHaveAttribute(
      'href',
      '/inmuebles',
    );
  });

  it('mientras_carga_muestra_el_esqueleto', () => {
    vi.mocked(apiFetch).mockReturnValue(new Promise(() => {}));

    render(<Destacados />);

    expect(screen.getByRole('status')).toHaveTextContent('Cargando inmuebles…');
  });

  it('si_la_api_falla_anuncia_el_error_y_permite_reintentar', async () => {
    vi.mocked(apiFetch)
      .mockRejectedValueOnce(new ApiError(0, 'Error de conexión', 'Sin conexión con la API.'))
      .mockResolvedValueOnce({
        content: [DESTACADO],
        nextCursor: null,
        totalElements: 1,
      });

    render(<Destacados />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Sin conexión con la API.');

    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Casa con jardín')).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledTimes(2);
  });
});
