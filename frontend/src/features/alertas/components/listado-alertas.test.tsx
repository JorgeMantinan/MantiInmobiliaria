import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ListadoAlertas } from '@/features/alertas/components/listado-alertas';
import { SesionContext, type ValorSesion } from '@/features/auth/session';
import type { AlertaBusqueda } from '@/features/alertas/types';
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

function alerta(id: number, nombre: string, activa = true): AlertaBusqueda {
  return {
    id,
    nombre,
    filtros: { estado: 'VENTA', municipio: 'Madrid', precioMin: 100000, precioMax: 250000 },
    activa,
    ultimoAvisoEn: null,
    creadoEn: '2026-10-01T10:00:00Z',
    actualizadoEn: '2026-10-01T10:00:00Z',
  };
}

function renderConSesion(valor: ValorSesion, ui: ReactNode) {
  return render(<SesionContext.Provider value={valor}>{ui}</SesionContext.Provider>);
}

/** Cualquier GET devuelve la lista `pedida`; PATCH/DELETE devuelven undefined. */
function instalarAlertas(pedidas: readonly AlertaBusqueda[]): void {
  vi.mocked(apiFetch).mockImplementation(async (_ruta, opciones) => {
    if (opciones?.method === 'PATCH') {
      const cuerpo = opciones.body as { activa?: boolean };
      const id = Number(_ruta.split('/').pop());
      const original = pedidas.find((fila) => fila.id === id);
      return { ...original!, activa: cuerpo.activa };
    }
    if (opciones?.method === 'DELETE') return undefined;
    return pedidas;
  });
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ListadoAlertas', () => {
  it('lista_lasAlertasConSuResumenDeFiltros', async () => {
    instalarAlertas([alerta(1, 'Pisos Madrid'), alerta(2, 'Áticos', false)]);

    renderConSesion(CON_SESION, <ListadoAlertas />);

    expect(await screen.findByText('Mostrando 2 alertas')).toBeInTheDocument();
    expect(screen.getByText('Pisos Madrid')).toBeInTheDocument();
    expect(screen.getByText('Áticos')).toBeInTheDocument();
    expect(screen.getAllByText(/En venta · Madrid/)).toHaveLength(2);
    // La pausada se distingue con su propia etiqueta.
    expect(screen.getByText('En pausa')).toBeInTheDocument();
    expect(screen.getAllByText('Activa')).toHaveLength(1);
    expect(screen.getAllByRole('link', { name: 'Ver búsqueda' })[0]).toHaveAttribute(
      'href',
      expect.stringContaining('/inmuebles?'),
    );
  });

  it('alPausar_haceUnPatchYReflejaElNuevoEstado', async () => {
    const usuario = userEvent.setup();
    instalarAlertas([alerta(1, 'Pisos Madrid')]);

    renderConSesion(CON_SESION, <ListadoAlertas />);
    await screen.findByText('Mostrando 1 alerta');

    await usuario.click(screen.getByRole('button', { name: 'Pausar alerta: Pisos Madrid' }));

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/alertas/1', {
      method: 'PATCH',
      body: { activa: false },
    });
    expect(await screen.findByText('En pausa')).toBeInTheDocument();
  });

  it('alEliminar_haceUnDeleteYQuitaLaFila', async () => {
    const usuario = userEvent.setup();
    instalarAlertas([alerta(1, 'Pisos Madrid'), alerta(2, 'Áticos')]);

    renderConSesion(CON_SESION, <ListadoAlertas />);
    await screen.findByText('Mostrando 2 alertas');

    await usuario.click(screen.getByRole('button', { name: 'Eliminar alerta: Pisos Madrid' }));

    expect(await screen.findByText('Mostrando 1 alerta')).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/alertas/1', { method: 'DELETE' });
    expect(screen.queryByText('Pisos Madrid')).not.toBeInTheDocument();
  });

  it('unFalloDeLaAccion_anunciaElErrorYConservaLaAlerta', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiFetch).mockImplementation(async (_ruta, opciones) => {
      if (opciones?.method === 'DELETE') throw new Error('no se ha podido eliminar');
      return [alerta(1, 'Pisos Madrid')];
    });

    renderConSesion(CON_SESION, <ListadoAlertas />);
    await screen.findByText('Mostrando 1 alerta');

    await usuario.click(screen.getByRole('button', { name: 'Eliminar alerta: Pisos Madrid' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('no se ha podido eliminar');
    expect(screen.getByText('Pisos Madrid')).toBeInTheDocument();
  });

  it('muestraElErrorDeCargaConUnReintento', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('servidor de alertas caído'));

    renderConSesion(CON_SESION, <ListadoAlertas />);

    expect(await screen.findByRole('alert')).toHaveTextContent('servidor de alertas caído');
    instalarAlertas([alerta(1, 'Pisos Madrid')]);
    await usuario.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Mostrando 1 alerta')).toBeInTheDocument();
  });

  it('sinAlertas_muestraElEstadoVacio', async () => {
    instalarAlertas([]);

    renderConSesion(CON_SESION, <ListadoAlertas />);

    expect(await screen.findByText('Aún no tienes alertas')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Buscar viviendas' })).toHaveAttribute(
      'href',
      '/inmuebles',
    );
  });

  it('conSesionAnonimaMuestraLaCta_sinLlamarALaApi', async () => {
    renderConSesion(SIN_SESION, <ListadoAlertas />);

    expect(
      await screen.findByText('Inicia sesión para gestionar tus alertas'),
    ).toBeInTheDocument();
    expect(apiFetch).not.toHaveBeenCalled();
  });
});
