import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { TarjetaInmueble } from '@/features/inmuebles/components/tarjeta-inmueble';
import type { InmuebleResumen } from '@/features/inmuebles/types';

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

const INMUEBLE: InmuebleResumen = {
  id: 7,
  titulo: 'Piso céntrico con ascensor',
  precio: 245000,
  tipo: 'PISO',
  estado: 'VENTA',
  superficieM2: 96,
  habitaciones: 3,
  banios: 1,
  municipio: 'Madrid',
  provincia: 'Madrid',
  imagenUrl: 'https://picsum.photos/seed/inm7/800/600',
  destacado: true,
  lat: 40.4168,
  lng: -3.7038,
  createdAt: '2026-01-01T10:00:00Z',
};

describe('TarjetaInmueble', () => {
  it('describe_la_foto_y_el_mini_mapa_con_alt_accesibles', () => {
    render(<TarjetaInmueble inmueble={INMUEBLE} />);

    expect(screen.getByAltText('Piso céntrico con ascensor en Madrid')).toBeInTheDocument();
    expect(
      screen.getByAltText('Mapa de la ubicación de Piso céntrico con ascensor en Madrid, Madrid'),
    ).toBeInTheDocument();
  });

  it('muestra_precio_datos_y_direccion_en_texto', () => {
    render(<TarjetaInmueble inmueble={INMUEBLE} />);

    expect(screen.getByText('245.000 €')).toBeInTheDocument();
    expect(screen.getByText('96 m² · 3 hab. · 1 baño')).toBeInTheDocument();
    expect(screen.getByText('Madrid')).toBeInTheDocument();
  });

  it('apunta_a_la_ficha_con_un_enlace_y_saca_el_tile_de_carto', () => {
    render(<TarjetaInmueble inmueble={INMUEBLE} />);

    const tile = screen.getByAltText(/Mapa de la ubicación de/);
    expect(tile.getAttribute('src')).toContain(
      'https://basemaps.cartocdn.com/rastertiles/voyager/16/',
    );
    expect(tile).toHaveAttribute('loading', 'lazy');
    expect(tile).toHaveAttribute('decoding', 'async');
    expect(screen.getAllByRole('link')[0]).toHaveAttribute('href', '/inmuebles/7');
  });
});
