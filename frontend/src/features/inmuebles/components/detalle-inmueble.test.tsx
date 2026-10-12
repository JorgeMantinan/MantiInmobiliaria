import type { ReactNode } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DetalleInmueble } from '@/features/inmuebles/components/detalle-inmueble';
import type { InmuebleDetalle } from '@/features/inmuebles/types';
import { ApiError, apiFetch } from '@/lib/api';

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

// Leaflet sólo vive en el navegador: se sustituye por un doble en jsdom.
vi.mock('@/features/inmuebles/components/mapa-detalle-wrapper', () => ({
  MapaDetalleWrapper: () => <div data-testid="mapa-detalle" />,
}));

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

const DETALLE: InmuebleDetalle = {
  id: 7,
  titulo: 'Piso céntrico con ascensor',
  descripcion: 'Luminoso piso reformado.\nSegunda planta con ascensor.',
  precio: 245000,
  superficieM2: 96,
  habitaciones: 3,
  banios: 1,
  tipo: 'PISO',
  estado: 'VENTA',
  direccion: 'Calle Mayor 1, 3º B',
  codigoPostal: '28013',
  municipio: 'Madrid',
  provincia: 'Madrid',
  // Sin código INE: la sección de precios de zona no consulta el API de mercado.
  codigoIne: null,
  lat: 40.4168,
  lng: -3.7038,
  imagenUrl: 'https://picsum.photos/seed/inm7/800/600',
  destacado: true,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-02T10:00:00Z',
};

const DETALLE_CON_CODIGO_INE: InmuebleDetalle = { ...DETALLE, codigoIne: '28079' };

const PRECIOS_ZONA = {
  codigoINE: '28079',
  anio: 2024,
  alquiler: { rentaMedianaEurM2Mes: 13.16, cuantiaMedianaEurMes: 868.8 },
  venta: null,
  fuente: 'Ministerio de Vivienda y Agenda Urbana',
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('DetalleInmueble', () => {
  it('mientras_carga_anuncia_el_estado_con_aria_live', () => {
    vi.mocked(apiFetch).mockReturnValue(new Promise(() => {}));

    render(<DetalleInmueble id={7} />);

    expect(screen.getByRole('status')).toHaveTextContent('Cargando el inmueble…');
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/inmuebles/7');
  });

  it('con_los_datos_muestra_precio_caracteristicas_direccion_y_mapa', async () => {
    vi.mocked(apiFetch).mockResolvedValue(DETALLE);

    render(<DetalleInmueble id={7} />);

    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(
      'Piso céntrico con ascensor',
    );
    expect(screen.getByText('245.000 €')).toBeInTheDocument();
    expect(screen.getByText('96 m²')).toBeInTheDocument();
    // La dirección aparece en la cabecera y en la sección "Ubicación".
    expect(screen.getAllByText('Calle Mayor 1, 3º B, 28013 Madrid')).toHaveLength(2);
    expect(screen.getByTestId('mapa-detalle')).toBeInTheDocument();
    expect(screen.getByText(/Luminoso piso reformado/)).toBeInTheDocument();
  });

  it('ante_un_error_de_red_muestra_el_aviso_y_permite_reintentar', async () => {
    vi.mocked(apiFetch)
      .mockRejectedValueOnce(
        new ApiError(0, 'Error de conexión', 'No se ha podido contactar con el servidor.'),
      )
      .mockResolvedValueOnce(DETALLE);

    render(<DetalleInmueble id={7} />);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No se ha podido contactar con el servidor.',
    );

    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
        'Piso céntrico con ascensor',
      );
    });
    expect(apiFetch).toHaveBeenCalledTimes(2);
  });

  it('con_un_404_de_la_api_no_presenta_la_ficha_como_encontrada', async () => {
    vi.mocked(apiFetch).mockRejectedValue(
      new ApiError(404, 'No encontrado', 'Inmueble 7 inexistente'),
    );

    render(<DetalleInmueble id={7} />);

    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(
      'Inmueble no encontrado',
    );
    expect(screen.queryByTestId('mapa-detalle')).not.toBeInTheDocument();
  });

  it('con_codigoIne_consulta_los_precios_de_zona_y_los_pinta', async () => {
    vi.mocked(apiFetch).mockImplementation(async (ruta: string) => {
      if (ruta.startsWith('/api/v1/mercado')) {
        return PRECIOS_ZONA as never;
      }
      return DETALLE_CON_CODIGO_INE as never;
    });

    render(<DetalleInmueble id={7} />);

    expect(
      await screen.findByRole('heading', { level: 2, name: 'Precio medio en la zona' }),
    ).toBeInTheDocument();
    // El texto está troceado: el municipio va en su propio <span>.
    expect(screen.getByText(/Alquiler medio en/)).toHaveTextContent('Madrid');
    expect(screen.getByText(/Fuente: Ministerio de Vivienda y Agenda Urbana/)).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/mercado?codigoINE=28079');
  });
});
