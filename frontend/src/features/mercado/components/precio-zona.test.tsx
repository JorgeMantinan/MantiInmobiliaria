import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PrecioZona } from '@/features/mercado/components/precio-zona';
import type { PreciosZona } from '@/features/mercado/types';
import { ApiError, apiFetch } from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

const AMBOS: PreciosZona = {
  codigoINE: '08019',
  anio: 2024,
  alquiler: { rentaMedianaEurM2Mes: 13.24, cuantiaMedianaEurMes: 900 },
  venta: {
    valorEurM2: 3204.2,
    ambito: 'PROVINCIA',
    provincia: 'Barcelona',
    anio: 2026,
    trimestre: 2,
  },
  fuente: 'Ministerio de Vivienda y Agenda Urbana',
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('PrecioZona', () => {
  it('con_datos_muestra_alquiler_venta_y_la_atribucion_legal', async () => {
    vi.mocked(apiFetch).mockResolvedValue(AMBOS);

    render(<PrecioZona codigoIne="08019" municipio="Barcelona" />);

    expect(await screen.findByRole('heading', { level: 2 })).toHaveTextContent(
      'Precio medio en la zona',
    );
    expect(screen.getByText(/Alquiler medio en/)).toHaveTextContent('13,24 €/m²/mes');
    expect(screen.getByText('(dato municipal)')).toBeInTheDocument();
    // El número de la venta se agrupa según la locale del runtime: se comprueba
    // la unidad y el matiz "provincial", no el formato exacto del grupo de miles.
    expect(screen.getByText(/Valor tasado de venta en/)).toHaveTextContent('€/m²');
    expect(screen.getByText(/Valor tasado de venta en/)).toHaveTextContent('(dato provincial)');
    expect(
      screen.getByText('Fuente: Ministerio de Vivienda y Agenda Urbana (SERPAVI / Valor tasado)'),
    ).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/mercado?codigoINE=08019');
  });

  it('sin_codigoIne_no_consulta_la_api_ni_pinta_nada', async () => {
    render(<PrecioZona codigoIne={null} municipio="Madrid" />);

    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it('mientras_carga_se_oculta', () => {
    vi.mocked(apiFetch).mockReturnValue(new Promise(() => {}));

    render(<PrecioZona codigoIne="08019" municipio="Barcelona" />);

    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });

  it('ante_un_404_de_mercado_se_oculta', async () => {
    vi.mocked(apiFetch).mockRejectedValue(
      new ApiError(404, 'No encontrado', 'No hay precios de mercado publicados'),
    );

    render(<PrecioZona codigoIne="99999" municipio="Pueblo" />);

    // No hay retry ni aviso: la sección simplemente no se pinta.
    await vi.waitFor(() => {
      expect(apiFetch).toHaveBeenCalledTimes(1);
    });
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });

  it('ante_un_error_de_red_se_oculta', async () => {
    vi.mocked(apiFetch).mockRejectedValue(
      new ApiError(0, 'Error de conexión', 'No se ha podido contactar con el servidor.'),
    );

    render(<PrecioZona codigoIne="08019" municipio="Barcelona" />);

    await vi.waitFor(() => {
      expect(apiFetch).toHaveBeenCalledTimes(1);
    });
    expect(screen.queryByText('Precio medio en la zona')).not.toBeInTheDocument();
  });

  it('con_solo_alquiler_no_pinta_la_linea_de_venta', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ ...AMBOS, venta: null });

    render(<PrecioZona codigoIne="28079" municipio="Madrid" />);

    expect(await screen.findByText(/Alquiler medio en/)).toBeInTheDocument();
    expect(screen.queryByText(/Valor tasado de venta/)).not.toBeInTheDocument();
  });

  it('con_solo_venta_no_pinta_la_linea_de_alquiler', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ ...AMBOS, alquiler: null });

    render(<PrecioZona codigoIne="08019" municipio="Barcelona" />);

    expect(await screen.findByText(/Valor tasado de venta en/)).toBeInTheDocument();
    expect(screen.queryByText(/Alquiler medio en/)).not.toBeInTheDocument();
  });

  it('con_ambos_bloques_vacios_se_oculta', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ ...AMBOS, alquiler: null, venta: null });

    render(<PrecioZona codigoIne="08019" municipio="Barcelona" />);

    await vi.waitFor(() => {
      expect(apiFetch).toHaveBeenCalledTimes(1);
    });
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });
});
