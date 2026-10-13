import type { ReactNode } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ListadoInmuebles } from '@/features/inmuebles/components/listado-inmuebles';
import type { InmuebleResumen, PaginaInmuebles } from '@/features/inmuebles/types';
import { apiFetch } from '@/lib/api';

const navegacion = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }));
const url = vi.hoisted(() => ({ actual: '' }));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: navegacion.replace, push: navegacion.push }),
  useSearchParams: () => new URLSearchParams(url.actual),
}));

vi.mock('next/link', () => ({
  default: ({ href, children }: Readonly<{ href: string; children: ReactNode }>) => (
    <a href={href}>{children}</a>
  ),
}));

// Leaflet sólo vive en el navegador: la vista de mapa se sustituye por un
// doble que expone cuántos inmuebles ha recibido del MISMO hook.
vi.mock('@/features/inmuebles/components/mapa-listado-wrapper', () => ({
  MapaListadoWrapper: ({ inmuebles }: Readonly<{ inmuebles: readonly InmuebleResumen[] }>) => (
    <div data-testid="mapa-listado" data-total={inmuebles.length} />
  ),
}));

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

function resumen(id: number, titulo: string): InmuebleResumen {
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

function pagina(content: readonly InmuebleResumen[], nextCursor: string | null): PaginaInmuebles {
  return { content, nextCursor, totalElements: 3 };
}

beforeEach(() => {
  vi.clearAllMocks();
  url.actual = '';
});

describe('ListadoInmuebles — vista de mapa', () => {
  it('al_pulsar_mapa_escribe_vista_mapa_en_la_url_y_pinta_el_mapa', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiFetch).mockResolvedValue(pagina([resumen(1, 'Piso Uno')], null));
    const vista = render(<ListadoInmuebles />);
    expect(await screen.findByText('Mostrando 1 de 3 inmuebles')).toBeInTheDocument();

    await usuario.click(screen.getByRole('button', { name: 'Mapa' }));

    expect(navegacion.replace).toHaveBeenCalledWith(
      '/inmuebles?vista=mapa',
      expect.objectContaining({ scroll: false }),
    );

    // Simula la navegación del router y comprueba el cambio de vista.
    url.actual = 'vista=mapa';
    vista.rerender(<ListadoInmuebles />);

    expect(await screen.findByTestId('mapa-listado')).toBeInTheDocument();
    expect(screen.queryByText('Mostrando 1 de 3 inmuebles')).not.toBeInTheDocument();
  });

  it('con_vista_mapa_en_la_url_arranca_en_el_mapa_y_no_manda_vista_al_backend', async () => {
    url.actual = 'vista=mapa';
    vi.mocked(apiFetch).mockResolvedValue(pagina([resumen(1, 'Piso Uno')], null));

    render(<ListadoInmuebles />);

    expect(await screen.findByTestId('mapa-listado')).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledTimes(1);
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/inmuebles?limit=20');
  });

  it('el_mapa_anuncia_por_aria_live_el_numero_de_inmuebles_cargados', async () => {
    url.actual = 'vista=mapa';
    vi.mocked(apiFetch).mockResolvedValue(
      pagina([resumen(1, 'Piso Uno'), resumen(2, 'Piso Dos')], null),
    );

    render(<ListadoInmuebles />);

    // El `role="status"` del esqueleto ya no está: sólo vive el del mapa.
    expect(await screen.findByText('2 inmuebles en el mapa')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('2 inmuebles en el mapa');
  });

  it('los_filtros_actualizan_el_mapa_sin_hacer_un_fetch_extra', async () => {
    url.actual = 'vista=mapa';
    vi.mocked(apiFetch)
      .mockResolvedValueOnce(pagina([resumen(1, 'Piso Uno'), resumen(2, 'Piso Dos')], null))
      .mockResolvedValueOnce(pagina([resumen(3, 'Piso Casa')], null));
    const vista = render(<ListadoInmuebles />);
    expect(await screen.findByTestId('mapa-listado')).toHaveAttribute('data-total', '2');
    expect(apiFetch).toHaveBeenCalledTimes(1);

    // Aplica un filtro desde la propia vista de mapa.
    const usuario = userEvent.setup();
    await usuario.selectOptions(screen.getByLabelText('Tipo'), 'CASA');
    await usuario.click(screen.getByRole('button', { name: 'Aplicar filtros' }));
    expect(navegacion.replace).toHaveBeenCalledWith(
      '/inmuebles?tipo=CASA&vista=mapa',
      expect.objectContaining({ scroll: false }),
    );

    // Simula la navegación: el MISMO hook trae la nueva página y el mapa la pinta.
    url.actual = 'tipo=CASA&vista=mapa';
    vista.rerender(<ListadoInmuebles />);

    expect(await screen.findByTestId('mapa-listado')).toHaveAttribute('data-total', '1');
    // Sólo la primera página y la del filtro: ni el mapa ni el conmutador fetchean.
    expect(apiFetch).toHaveBeenCalledTimes(2);
    expect(apiFetch).toHaveBeenLastCalledWith('/api/v1/inmuebles?tipo=CASA&limit=20');
  });

  it('alternar_de_lista_a_mapa_no_vuelve_a_llamar_a_la_api', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiFetch).mockResolvedValue(pagina([resumen(1, 'Piso Uno')], null));
    const vista = render(<ListadoInmuebles />);
    expect(await screen.findByText('Mostrando 1 de 3 inmuebles')).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledTimes(1);

    await usuario.click(screen.getByRole('button', { name: 'Mapa' }));
    url.actual = 'vista=mapa';
    vista.rerender(<ListadoInmuebles />);
    expect(await screen.findByTestId('mapa-listado')).toBeInTheDocument();

    await usuario.click(screen.getByRole('button', { name: 'Lista' }));
    url.actual = '';
    vista.rerender(<ListadoInmuebles />);

    expect(await screen.findByText('Mostrando 1 de 3 inmuebles')).toBeInTheDocument();
    // La URL cambia, pero los datos son los mismos: cero peticiones nuevas.
    expect(apiFetch).toHaveBeenCalledTimes(1);
  });

  it('acumula_en_el_mapa_los_inmuebles_del_cargar_mas', async () => {
    const usuario = userEvent.setup();
    url.actual = 'vista=mapa';
    vi.mocked(apiFetch)
      .mockResolvedValueOnce(pagina([resumen(1, 'Piso Uno'), resumen(2, 'Piso Dos')], 'c1'))
      .mockResolvedValueOnce(pagina([resumen(3, 'Piso Tres')], null));
    render(<ListadoInmuebles />);
    expect(await screen.findByTestId('mapa-listado')).toHaveAttribute('data-total', '2');

    await usuario.click(screen.getByRole('button', { name: 'Cargar más' }));

    await waitFor(() => {
      expect(screen.getByTestId('mapa-listado')).toHaveAttribute('data-total', '3');
    });
    expect(apiFetch).toHaveBeenLastCalledWith('/api/v1/inmuebles?cursor=c1&limit=20');
  });

  it('en_vista_mapa_sin_resultados_muestra_el_estado_vacio_de_la_lista', async () => {
    url.actual = 'vista=mapa';
    vi.mocked(apiFetch).mockResolvedValue(pagina([], null));

    render(<ListadoInmuebles />);

    expect(await screen.findByText('Sin resultados')).toBeInTheDocument();
    // También lo ofrece la barra de filtros (mismo `limpiar`).
    expect((await screen.findAllByRole('button', { name: 'Limpiar filtros' })).length).toBeGreaterThan(
      0,
    );
    expect(screen.queryByTestId('mapa-listado')).not.toBeInTheDocument();
  });

  it('limpiar_filtros_desde_el_mapa_conserva_la_vista_compartible', async () => {
    const usuario = userEvent.setup();
    url.actual = 'vista=mapa&tipo=CASA';
    vi.mocked(apiFetch).mockResolvedValue(pagina([], null));

    render(<ListadoInmuebles />);
    await usuario.click(await screen.findByRole('button', { name: 'Limpiar filtros' }));

    expect(navegacion.replace).toHaveBeenCalledWith(
      '/inmuebles?vista=mapa',
      expect.objectContaining({ scroll: false }),
    );
  });
});
