import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  MARGEN_BOUNDS,
  MapaListado,
  ZOOM_MAXIMO_BOUNDS,
} from '@/features/inmuebles/components/mapa-listado';
import type { InmuebleResumen } from '@/features/inmuebles/types';

/** Doble del mapa: captura `fitBounds` para poder asertar el encuadre. */
const map = vi.hoisted(() => ({ fitBounds: vi.fn() }));

type PropsMarcador = Readonly<{
  position: readonly [number, number];
  alt?: string;
  icon?: Readonly<{ options?: Readonly<{ html?: string }> }>;
  children?: ReactNode;
}>;

// Leaflet vive en el navegador: se sustituye por un doble en jsdom; la
// matemática real de bounds la aporta `leaflet` (no se mockea).
vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }: Readonly<{ children?: ReactNode }>) => (
    <div data-testid="mapa">{children}</div>
  ),
  TileLayer: () => null,
  Marker: ({ position, alt, icon, children }: PropsMarcador) => (
    <div
      data-testid="marcador"
      data-alt={alt}
      data-lat={position[0]}
      data-lng={position[1]}
      data-icon={icon?.options?.html ?? ''}
    >
      {children}
    </div>
  ),
  Popup: ({ children }: Readonly<{ children?: ReactNode }>) => (
    <div data-testid="popup">{children}</div>
  ),
  useMap: () => map,
}));

vi.mock('next/link', () => ({
  default: ({ href, children }: Readonly<{ href: string; children: ReactNode }>) => (
    <a href={href}>{children}</a>
  ),
}));

function resumen(id: number, titulo: string, lat: number, lng: number): InmuebleResumen {
  return {
    id,
    titulo,
    precio: 245000,
    tipo: 'PISO',
    estado: 'VENTA',
    superficieM2: 96,
    habitaciones: 3,
    banios: 1,
    municipio: 'Madrid',
    provincia: 'Madrid',
    imagenUrl: null,
    destacado: false,
    lat,
    lng,
    createdAt: '2026-01-01T10:00:00Z',
  };
}

const MADRID = resumen(1, 'Piso céntrico', 40.4168, -3.7038);
const BARCELONA = resumen(2, 'Ático en Gràcia', 41.3851, 2.1734);
const VALENCIA = resumen(3, 'Casa junto al Turia', 39.4699, -0.3763);
const CARGADOS = [MADRID, BARCELONA, VALENCIA];

beforeEach(() => {
  vi.clearAllMocks();
});

describe('MapaListado', () => {
  it('pinta_un_marcador_por_cada_inmueble_cargado', () => {
    render(<MapaListado inmuebles={CARGADOS} />);

    const marcadores = screen.getAllByTestId('marcador');
    expect(marcadores).toHaveLength(3);
    expect(marcadores[0]).toHaveAttribute('data-alt', 'Ubicación de Piso céntrico');
    expect(marcadores[0]).toHaveAttribute('data-lat', '40.4168');
    expect(marcadores[0]).toHaveAttribute('data-lng', '-3.7038');
    expect(marcadores[1]).toHaveAttribute('data-alt', 'Ubicación de Ático en Gràcia');
  });

  it('usa_el_pin_custom_de_madera_y_no_el_icono_azul_por_defecto', () => {
    render(<MapaListado inmuebles={[MADRID]} />);

    const html = screen.getByTestId('marcador').getAttribute('data-icon');
    expect(html).toContain('bg-acento-nogal');
    expect(html).toContain('border-superficie');
    expect(html).toContain('rounded-full');
  });

  it('el_popup_muestra_titulo_precio_y_enlace_a_la_ficha', () => {
    render(<MapaListado inmuebles={[MADRID]} />);

    const popup = screen.getByTestId('popup');
    expect(popup).toHaveTextContent('Piso céntrico');
    expect(popup).toHaveTextContent('245.000 €');
    const enlace = screen.getByRole('link', { name: 'Ver ficha' });
    expect(enlace).toHaveAttribute('href', '/inmuebles/1');
  });

  it('ajusta_los_bounds_a_todos_los_marcadores_con_margen', () => {
    render(<MapaListado inmuebles={CARGADOS} />);

    expect(map.fitBounds).toHaveBeenCalledTimes(1);
    const [bounds, opciones] = map.fitBounds.mock.calls[0];
    expect(bounds.contains([40.4168, -3.7038])).toBe(true);
    expect(bounds.contains([41.3851, 2.1734])).toBe(true);
    expect(bounds.contains([39.4699, -0.3763])).toBe(true);
    expect(opciones).toEqual({
      padding: [MARGEN_BOUNDS, MARGEN_BOUNDS],
      maxZoom: ZOOM_MAXIMO_BOUNDS,
    });
  });

  it('vuelve_a_encuadrar_cuando_se_acumulan_mas_inmuebles', () => {
    const { rerender } = render(<MapaListado inmuebles={[MADRID]} />);
    expect(map.fitBounds).toHaveBeenCalledTimes(1);

    rerender(<MapaListado inmuebles={[MADRID, BARCELONA]} />);

    expect(map.fitBounds).toHaveBeenCalledTimes(2);
    const [bounds] = map.fitBounds.mock.calls[1];
    expect(bounds.contains([41.3851, 2.1734])).toBe(true);
  });
});
