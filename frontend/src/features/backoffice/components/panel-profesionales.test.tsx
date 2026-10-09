import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PanelProfesionales } from '@/features/backoffice/components/panel-profesionales';
import type { EstadoProfesional, EstadoProfesionalRespuesta, ProfesionalAdmin } from '@/features/backoffice/types';
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

function profesional(
  id: number,
  nombre: string,
  estado: EstadoProfesional = 'PENDIENTE',
): ProfesionalAdmin {
  return {
    id,
    nombre,
    email: `${nombre.toLowerCase().split(' ')[0]}@ejemplo.com`,
    telefono: '600000001',
    municipio: 'Oviedo',
    gremios: ['fontanero', 'electricista'],
    estado,
    creadoEn: '2026-10-01T10:00:00Z',
  };
}

/** Elemento `<li>` que contiene la fila de un profesional concreto. */
function filaDe(nombre: string): HTMLElement {
  const encabezado = screen.getByRole('heading', { name: nombre });
  const item = encabezado.closest('li');
  if (item === null) throw new Error(`no se encontró la fila de ${nombre}`);
  return item;
}

beforeEach(() => {
  vi.clearAllMocks();
  url.actual = '';
});

describe('PanelProfesionales', () => {
  it('por_defecto_pide_la_pestana_pendiente_y_la_marca_activa', async () => {
    vi.mocked(apiFetch).mockResolvedValue([profesional(1, 'Luis Fontanero')]);

    render(<PanelProfesionales />);

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/admin/profesionales?estado=PENDIENTE');
    expect(await screen.findByRole('heading', { name: 'Luis Fontanero' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pendiente' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByRole('button', { name: 'Activo' })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
  });

  it('al_cambiar_de_pestana_reemplaza_la_url_y_recarga_con_el_nuevo_estado', async () => {
    vi.mocked(apiFetch).mockResolvedValue([]);
    const vista = render(<PanelProfesionales />);
    await waitFor(() => expect(apiFetch).toHaveBeenCalledTimes(1));

    await userEvent.click(screen.getByRole('button', { name: 'Activo' }));

    expect(navegacion.replace).toHaveBeenCalledWith(
      '/admin/profesionales?estado=ACTIVO',
      expect.objectContaining({ scroll: false }),
    );

    // La URL cambia (como hace el router) y el panel recarga la pestaña.
    url.actual = 'estado=ACTIVO';
    vista.rerender(<PanelProfesionales />);

    await waitFor(() =>
      expect(apiFetch).toHaveBeenLastCalledWith('/api/v1/admin/profesionales?estado=ACTIVO'),
    );
    expect(await screen.findByText('Sin profesionales en esta pestaña')).toBeInTheDocument();
    expect(screen.getByText('No hay profesionales con estado activo.')).toBeInTheDocument();
  });

  it('aprobar_hace_el_patch_y_pinta_el_estado_nuevo_en_optimista', async () => {
    const luis = profesional(7, 'Luis Fontanero');
    let confirmarPatch!: (valor: EstadoProfesionalRespuesta) => void;
    vi.mocked(apiFetch)
      .mockResolvedValueOnce([luis])
      .mockReturnValueOnce(
        new Promise<EstadoProfesionalRespuesta>((resolver) => {
          confirmarPatch = resolver;
        }),
      );

    render(<PanelProfesionales />);
    expect(await screen.findByRole('heading', { name: 'Luis Fontanero' })).toBeInTheDocument();

    const usuario = userEvent.setup();
    await usuario.click(screen.getByRole('button', { name: 'Aprobar' }));
    expect(screen.getByRole('group', { name: 'Confirmar: Aprobar' })).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Sí, aprobar' }));

    // Optimista: el badge ya está ACTIVO mientras el PATCH sigue en vuelo.
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/admin/profesionales/7', {
      method: 'PATCH',
      body: { estado: 'ACTIVO' },
    });
    expect(within(filaDe('Luis Fontanero')).getByText('Activo')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Rechazar' })).toBeDisabled();

    confirmarPatch({ id: 7, estado: 'ACTIVO' });
    expect(await screen.findByText('Luis Fontanero ahora está en estado Activo.')).toBeInTheDocument();
  });

  it('si_el_patch_falla_la_fila_vuelve_a_pendiente_y_muestra_el_error', async () => {
    const luis = profesional(7, 'Luis Fontanero');
    vi.mocked(apiFetch)
      .mockResolvedValueOnce([luis])
      .mockRejectedValueOnce(new Error('la API está caída'));

    render(<PanelProfesionales />);
    expect(await screen.findByRole('heading', { name: 'Luis Fontanero' })).toBeInTheDocument();

    const usuario = userEvent.setup();
    await usuario.click(screen.getByRole('button', { name: 'Rechazar' }));
    await usuario.click(screen.getByRole('button', { name: 'Sí, rechazar' }));

    const error = await screen.findByRole('alert');
    expect(error).toHaveTextContent('la API está caída');
    expect(within(filaDe('Luis Fontanero')).getByText('Pendiente')).toBeInTheDocument();
    expect(within(filaDe('Luis Fontanero')).queryByText('Rechazado')).not.toBeInTheDocument();
  });

  it('muestra_el_vacio_de_la_pestana_cuando_no_hay_profesionales', async () => {
    url.actual = 'estado=RECHAZADO';
    vi.mocked(apiFetch).mockResolvedValue([]);

    render(<PanelProfesionales />);

    expect(await screen.findByText('Sin profesionales en esta pestaña')).toBeInTheDocument();
    expect(screen.getByText('No hay profesionales con estado rechazado.')).toBeInTheDocument();
  });

  it('si_la_carga_falla_muestra_el_error_y_permite_reintentar', async () => {
    vi.mocked(apiFetch)
      .mockRejectedValueOnce(new Error('la API está caída'))
      .mockResolvedValueOnce([profesional(1, 'Luis Fontanero')]);

    render(<PanelProfesionales />);

    expect(await screen.findByRole('alert')).toHaveTextContent('la API está caída');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('heading', { name: 'Luis Fontanero' })).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledTimes(2);
  });

  it('un_profesional_rechazado_solo_ofrece_aprobar', async () => {
    url.actual = 'estado=RECHAZADO';
    vi.mocked(apiFetch).mockResolvedValue([profesional(9, 'Marta Rechazada', 'RECHAZADO')]);

    render(<PanelProfesionales />);
    expect(await screen.findByRole('heading', { name: 'Marta Rechazada' })).toBeInTheDocument();

    expect(screen.getByRole('button', { name: 'Aprobar' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Rechazar' })).not.toBeInTheDocument();
  });
});
