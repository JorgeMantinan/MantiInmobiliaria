import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PanelInmuebles } from '@/features/backoffice/components/panel-inmuebles';
import type { InmuebleResumen } from '@/features/inmuebles/types';
import { apiFetch } from '@/lib/api';

const navegacion = vi.hoisted(() => ({ replace: vi.fn() }));
const url = vi.hoisted(() => ({ actual: '' }));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: navegacion.replace, push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(url.actual),
}));

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

function inmueble(id: number, titulo: string, destacado = false): InmuebleResumen {
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
    destacado,
    lat: 40.4168,
    lng: -3.7038,
    createdAt: '2026-10-01T10:00:00Z',
  };
}

/** Página vacía del envelope del endpoint público. */
function pagina(
  filas: readonly InmuebleResumen[],
  extras: { nextCursor?: string | null; totalElements?: number | null } = {},
) {
  return {
    content: filas,
    nextCursor: extras.nextCursor ?? null,
    totalElements: extras.totalElements ?? filas.length,
  };
}

/** Elemento `<li>` que contiene la fila de un inmueble concreto. */
function filaDe(titulo: string): HTMLElement {
  const encabezado = screen.getByRole('heading', { name: titulo });
  const item = encabezado.closest('li');
  if (item === null) throw new Error(`no se encontró la fila de ${titulo}`);
  return item;
}

beforeEach(() => {
  vi.clearAllMocks();
  url.actual = '';
});

describe('PanelInmuebles — listado', () => {
  it('carga_y_pinta_los_inmuebles_con_sus_badges', async () => {
    vi.mocked(apiFetch).mockResolvedValue(
      pagina([inmueble(1, 'Piso A'), inmueble(2, 'Ático B', true)]),
    );

    render(<PanelInmuebles />);

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/inmuebles?limit=20');
    expect(await screen.findByRole('heading', { name: 'Piso A' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Ático B' })).toBeInTheDocument();
    expect(within(filaDe('Ático B')).getByText('Destacado')).toBeInTheDocument();
    expect(within(filaDe('Piso A')).getByText('En venta')).toBeInTheDocument();
    expect(screen.getByText('2 inmuebles.')).toBeInTheDocument();
  });

  it('muestra_el_vacio_cuando_no_hay_resultados', async () => {
    vi.mocked(apiFetch).mockResolvedValue(pagina([]));

    render(<PanelInmuebles />);

    expect(await screen.findByText('Sin inmuebles')).toBeInTheDocument();
  });

  it('si_la_carga_falla_muestra_el_error_y_permite_reintentar', async () => {
    vi.mocked(apiFetch)
      .mockRejectedValueOnce(new Error('la API está caída'))
      .mockResolvedValueOnce(pagina([inmueble(1, 'Piso A')]));

    render(<PanelInmuebles />);

    expect(await screen.findByRole('alert')).toHaveTextContent('la API está caída');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('heading', { name: 'Piso A' })).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledTimes(2);
  });
});

describe('PanelInmuebles — filtros', () => {
  it('la_pestana_de_estado_reemplaza_la_url_y_recarga_con_el_filtro', async () => {
    vi.mocked(apiFetch).mockResolvedValue(pagina([]));
    const vista = render(<PanelInmuebles />);
    await waitFor(() => expect(apiFetch).toHaveBeenCalledTimes(1));

    await userEvent.click(screen.getByRole('button', { name: 'En alquiler' }));

    expect(navegacion.replace).toHaveBeenCalledWith('/admin/inmuebles?estado=ALQUILER', {
      scroll: false,
    });

    // La URL cambia (como hace el router) y el panel recarga.
    url.actual = 'estado=ALQUILER';
    vista.rerender(<PanelInmuebles />);

    await waitFor(() =>
      expect(apiFetch).toHaveBeenLastCalledWith('/api/v1/inmuebles?estado=ALQUILER&limit=20'),
    );
    expect(screen.getByRole('button', { name: 'En alquiler' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('el_buscador_de_municipio_y_el_filtro_de_destacados_se_sincronizan_en_la_url', async () => {
    vi.mocked(apiFetch).mockResolvedValue(pagina([]));
    render(<PanelInmuebles />);
    await waitFor(() => expect(apiFetch).toHaveBeenCalledTimes(1));

    const usuario = userEvent.setup();
    await usuario.type(screen.getByLabelText('Municipio'), 'Gijón');
    await usuario.click(screen.getByLabelText('Sólo destacados'));
    await usuario.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(navegacion.replace).toHaveBeenCalledWith(
      '/admin/inmuebles?municipio=Gij%C3%B3n&destacado=true',
      { scroll: false },
    );
  });

  it('al_cambiar_de_pestana_se_preservan_el_municipio_y_el_destacado_vigentes', async () => {
    url.actual = 'municipio=Gij%C3%B3n&destacado=true';
    vi.mocked(apiFetch).mockResolvedValue(pagina([]));
    render(<PanelInmuebles />);
    await waitFor(() => expect(apiFetch).toHaveBeenCalledTimes(1));

    await userEvent.click(screen.getByRole('button', { name: 'En alquiler' }));

    // El cambio de pestaña sólo toca `estado`: municipio y destacado siguen.
    expect(navegacion.replace).toHaveBeenCalledWith(
      '/admin/inmuebles?estado=ALQUILER&municipio=Gij%C3%B3n&destacado=true',
      { scroll: false },
    );
  });
});

describe('PanelInmuebles — borrado optimista', () => {
  it('al_confirmar_la_fila_sale_ya_y_el_delete_se_envia', async () => {
    vi.mocked(apiFetch)
      .mockResolvedValueOnce(pagina([inmueble(1, 'Piso A'), inmueble(2, 'Ático B')]))
      .mockResolvedValueOnce(undefined); // DELETE 204

    render(<PanelInmuebles />);
    expect(await screen.findByRole('heading', { name: 'Piso A' })).toBeInTheDocument();

    const usuario = userEvent.setup();
    await usuario.click(within(filaDe('Piso A')).getByRole('button', { name: 'Eliminar' }));
    expect(screen.getByRole('group', { name: 'Confirmar: Eliminar' })).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Sí, eliminar' }));

    // Optimista: la fila ya no está mientras el DELETE sigue en vuelo.
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/admin/inmuebles/1', { method: 'DELETE' });
    expect(screen.queryByRole('heading', { name: 'Piso A' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Ático B' })).toBeInTheDocument();

    expect(
      await screen.findByText('«Piso A» se ha eliminado del portal.'),
    ).toBeInTheDocument();
  });

  it('si_el_delete_falla_la_fila_vuelve_a_su_sitio_y_se_ve_el_error', async () => {
    vi.mocked(apiFetch)
      .mockResolvedValueOnce(pagina([inmueble(1, 'Piso A'), inmueble(2, 'Ático B')]))
      .mockRejectedValueOnce(new Error('sin permisos'));

    render(<PanelInmuebles />);
    expect(await screen.findByRole('heading', { name: 'Piso A' })).toBeInTheDocument();

    const usuario = userEvent.setup();
    await usuario.click(within(filaDe('Piso A')).getByRole('button', { name: 'Eliminar' }));
    await usuario.click(screen.getByRole('button', { name: 'Sí, eliminar' }));

    const error = await screen.findByRole('alert');
    expect(error).toHaveTextContent('sin permisos');
    // Rollback: la fila vuelve (al final: no se conoce el índice exacto tras el filtro).
    expect(screen.getByRole('heading', { name: 'Piso A' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Ático B' })).toBeInTheDocument();
  });
});

describe('PanelInmuebles — carrera entre "cargar más" y filtros', () => {
  it('la_respuesta_vieja_del_cargar_mas_no_pisa_la_pagina_de_los_filtros_nuevos', async () => {
    /**
     * Orden de llamadas: (1) primera página sin filtros con cursor,
     * (2) "cargar más" con el cursor viejo, (3) primera página de los
     * filtros nuevos (`?estado=ALQUILER`). Se resuelve PRIMERO la nueva y
     * DESPUÉS la vieja: sin invalidar la generación en el effect, la vieja
     * pisaría la página y dejaría el esqueleto (pagina.filtros ≠ filtros).
     */
    let resolverVieja!: (valor: unknown) => void;
    let resolverNueva!: (valor: unknown) => void;
    vi.mocked(apiFetch)
      .mockResolvedValueOnce(pagina([inmueble(1, 'Piso A')], { nextCursor: 'CURSOR_VIEJO' }))
      .mockImplementationOnce(
        () =>
          new Promise((resolver) => {
            resolverVieja = resolver;
          }),
      )
      .mockImplementationOnce(
        () =>
          new Promise((resolver) => {
            resolverNueva = resolver;
          }),
      );

    const vista = render(<PanelInmuebles />);
    expect(await screen.findByRole('heading', { name: 'Piso A' })).toBeInTheDocument();

    // "Cargar más" en vuelo…
    await userEvent.click(screen.getByRole('button', { name: 'Cargar más' }));
    expect(apiFetch).toHaveBeenCalledTimes(2);

    // …y cambio de filtros mientras sigue en vuelo: fetch pendiente ⇒ esqueleto.
    url.actual = 'estado=ALQUILER';
    vista.rerender(<PanelInmuebles />);
    await waitFor(() => expect(apiFetch).toHaveBeenCalledTimes(3));
    expect(screen.getByText('Cargando listado…')).toBeInTheDocument();

    // Llega primero la página NUEVA (filtros ALQUILER).
    await act(async () => {
      resolverNueva(pagina([inmueble(2, 'Piso Alquiler')]));
    });
    expect(await screen.findByRole('heading', { name: 'Piso Alquiler' })).toBeInTheDocument();

    // …y DESPUÉS la respuesta VIEJA del "cargar más": debe descartarse.
    await act(async () => {
      resolverVieja(pagina([inmueble(9, 'Viejo cursor')], { totalElements: 5 }));
    });

    // Sigue la página de los filtros nuevos: sin filas viejas ni esqueleto
    // infinito (pagina.filtros sigue siendo el de ALQUILER).
    expect(screen.getByRole('heading', { name: 'Piso Alquiler' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Viejo cursor' })).not.toBeInTheDocument();
    expect(screen.queryByText('Cargando listado…')).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Piso A' })).not.toBeInTheDocument();
  });
});
