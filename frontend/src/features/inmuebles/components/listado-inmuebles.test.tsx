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
  default: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
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

/** Deja correr los microtasks de las promesas ya resueltas. */
const cederElTurno = () => new Promise<void>((resolver) => setTimeout(resolver, 0));

beforeEach(() => {
  vi.clearAllMocks();
  url.actual = '';
});

describe('ListadoInmuebles', () => {
  it('traduce_los_filtros_de_la_url_a_la_query_del_endpoint', async () => {
    url.actual = 'estado=VENTA&precioMax=200000&sort=precio_asc';
    vi.mocked(apiFetch).mockResolvedValue(pagina([], null));

    render(<ListadoInmuebles />);

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith(
        '/inmuebles?estado=VENTA&precioMax=200000&sort=precio_asc&limit=20',
      );
    });
    expect(await screen.findByText('Sin resultados')).toBeInTheDocument();
  });

  it('al_aplicar_filtros_sustituye_la_url_con_router_replace', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiFetch).mockResolvedValue(pagina([], null));

    render(<ListadoInmuebles />);
    await usuario.type(screen.getByLabelText('Municipio'), 'Madrid');
    await usuario.selectOptions(screen.getByLabelText('Estado'), 'VENTA');
    await usuario.click(screen.getByRole('button', { name: 'Aplicar filtros' }));

    expect(navegacion.replace).toHaveBeenCalledWith(
      '/inmuebles?estado=VENTA&municipio=Madrid',
      expect.objectContaining({ scroll: false }),
    );
  });

  it('cargar_mas_acumula_sin_repetir_los_inmuebles_y_usa_el_cursor', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiFetch)
      .mockResolvedValueOnce(pagina([resumen(1, 'Piso Uno'), resumen(2, 'Piso Dos')], 'c1'))
      .mockResolvedValueOnce(pagina([resumen(2, 'Piso Dos'), resumen(3, 'Piso Tres')], null));

    render(<ListadoInmuebles />);
    expect(await screen.findByText('Mostrando 2 de 3 inmuebles')).toBeInTheDocument();

    await usuario.click(screen.getByRole('button', { name: 'Cargar más' }));

    expect(await screen.findByText('Mostrando 3 de 3 inmuebles')).toBeInTheDocument();
    expect(apiFetch).toHaveBeenLastCalledWith('/inmuebles?cursor=c1&limit=20');
    expect(screen.getAllByText('Piso Dos')).toHaveLength(1);
    expect(screen.getAllByText('Piso Tres')).toHaveLength(1);
  });

  it('muestra_el_error_de_carga_con_un_reintento', async () => {
    vi.mocked(apiFetch)
      .mockRejectedValueOnce(new Error('la API está caída'))
      .mockResolvedValueOnce(pagina([resumen(1, 'Piso Uno')], null));

    render(<ListadoInmuebles />);

    expect(await screen.findByRole('alert')).toHaveTextContent('la API está caída');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Mostrando 1 de 3 inmuebles')).toBeInTheDocument();
  });

  it('descarta_la_respuesta_de_un_reintentar_obsoleto_cuando_cambian_los_filtros', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('fallo inicial'));
    const vista = render(<ListadoInmuebles />);
    expect(await screen.findByRole('alert')).toHaveTextContent('fallo inicial');

    // Reintento que sigue en vuelo...
    let resolverReintento!: (valor: PaginaInmuebles) => void;
    vi.mocked(apiFetch).mockReturnValueOnce(
      new Promise<PaginaInmuebles>((resolver) => {
        resolverReintento = resolver;
      }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    // ...mientras cambian los filtros: nueva búsqueda, también en vuelo.
    url.actual = 'tipo=CASA';
    let resolverNueva!: (valor: PaginaInmuebles) => void;
    vi.mocked(apiFetch).mockReturnValueOnce(
      new Promise<PaginaInmuebles>((resolver) => {
        resolverNueva = resolver;
      }),
    );
    vista.rerender(<ListadoInmuebles />);

    // La búsqueda de los filtros actuales se pinta PRIMERO...
    resolverNueva(pagina([resumen(2, 'Piso Nuevo')], null));
    expect(await screen.findByText('Piso Nuevo')).toBeInTheDocument();

    // ...y sólo DESPUÉS llega la respuesta obsoleta del reintento: sin la
    // guarda de generación pisaría la lista nueva y este getByText fallaría.
    resolverReintento(pagina([resumen(99, 'Obsoleto')], null));
    await cederElTurno();
    expect(screen.getByText('Piso Nuevo')).toBeInTheDocument();
    expect(screen.queryByText('Obsoleto')).not.toBeInTheDocument();
  });

  it('no_acumula_la_pagina_de_un_cargar_mas_lanzado_con_filtros_anteriores', async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(
      pagina([resumen(1, 'Piso Uno'), resumen(2, 'Piso Dos')], 'c1'),
    );
    const vista = render(<ListadoInmuebles />);
    expect(await screen.findByText('Mostrando 2 de 3 inmuebles')).toBeInTheDocument();

    // "Cargar más" que queda en vuelo...
    let resolverCarga!: (valor: PaginaInmuebles) => void;
    vi.mocked(apiFetch).mockReturnValueOnce(
      new Promise<PaginaInmuebles>((resolver) => {
        resolverCarga = resolver;
      }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Cargar más' }));

    // ...y cambian los filtros antes de que responda.
    url.actual = 'tipo=CASA';
    let resolverNueva!: (valor: PaginaInmuebles) => void;
    vi.mocked(apiFetch).mockReturnValueOnce(
      new Promise<PaginaInmuebles>((resolver) => {
        resolverNueva = resolver;
      }),
    );
    vista.rerender(<ListadoInmuebles />);

    // La página de los filtros actuales se pinta PRIMERO...
    resolverNueva(pagina([resumen(10, 'Piso Nuevo')], null));
    expect(await screen.findByText('Piso Nuevo')).toBeInTheDocument();

    // ...y SÓLO DESPUÉS llega la página obsoleta del "cargar más": sin la
    // guarda reconstruiría la lista vieja y 'Piso Nuevo' desaparecería.
    resolverCarga(pagina([resumen(3, 'Piso Obsoleto')], null));
    await cederElTurno();
    expect(screen.getByText('Piso Nuevo')).toBeInTheDocument();
    expect(screen.queryByText('Piso Obsoleto')).not.toBeInTheDocument();
  });
});
