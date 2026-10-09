import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ListaSolicitudes } from '@/features/backoffice/components/lista-solicitudes';
import type { SolicitudAdmin } from '@/features/backoffice/types';
import { apiFetch } from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

function solicitud(
  id: number,
  nombreContacto: string,
  descripcion = 'Se ha roto el grifo del baño y pierde agua constante.',
): SolicitudAdmin {
  return {
    id,
    nombreContacto,
    emailContacto: 'ana@ejemplo.com',
    telefono: '600000002',
    municipio: 'Gijón',
    descripcion,
    estado: 'NUEVA',
    gremios: ['fontanero'],
    creadoEn: '2026-10-01T10:00:00Z',
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ListaSolicitudes', () => {
  it('renderiza_las_filas_con_contacto_municipio_gremios_y_estado', async () => {
    vi.mocked(apiFetch).mockResolvedValue([solicitud(1, 'Ana Pérez')]);

    const { container } = render(<ListaSolicitudes />);

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/admin/solicitudes');
    const fila = (await screen.findByRole('heading', { name: 'Ana Pérez' })).closest('li');
    expect(fila).not.toBeNull();
    if (fila === null) return;

    expect(within(fila).getByRole('link', { name: 'ana@ejemplo.com' })).toHaveAttribute(
      'href',
      'mailto:ana@ejemplo.com',
    );
    expect(within(fila).getByText('Gijón')).toBeInTheDocument();
    expect(within(fila).getByText('Fontanero')).toBeInTheDocument();
    expect(within(fila).getByText('Nueva')).toBeInTheDocument();
    expect(within(fila).getByText(/Se ha roto el grifo del baño/)).toBeInTheDocument();
    expect(container.querySelector('time')).toHaveAttribute('datetime', '2026-10-01T10:00:00Z');
    // Sólo lectura: la única acción es expandir la descripción.
    expect(within(fila).queryByRole('button', { name: 'Cerrar' })).not.toBeInTheDocument();
  });

  it('trunca_la_descripcion_larga_y_la_expande_al_pulsar_el_boton', async () => {
    const descripcion = `${'Trabajo '.repeat(20)}FINAL_MARCA`;
    vi.mocked(apiFetch).mockResolvedValue([solicitud(2, 'Ana Pérez', descripcion)]);

    render(<ListaSolicitudes />);
    expect(await screen.findByRole('heading', { name: 'Ana Pérez' })).toBeInTheDocument();

    const desplegar = screen.getByRole('button', { name: 'Ver descripción completa' });
    expect(desplegar).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText(/FINAL_MARCA/)).not.toBeInTheDocument();

    await userEvent.click(desplegar);

    expect(screen.getByText(/FINAL_MARCA/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ver menos' })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
  });

  it('muestra_el_vacio_cuando_no_hay_solicitudes', async () => {
    vi.mocked(apiFetch).mockResolvedValue([]);

    render(<ListaSolicitudes />);

    expect(await screen.findByText('Sin solicitudes')).toBeInTheDocument();
    expect(
      screen.getByText('Todavía no ha llegado ninguna solicitud de presupuesto.'),
    ).toBeInTheDocument();
  });

  it('muestra_el_error_de_carga_y_permite_reintentar', async () => {
    vi.mocked(apiFetch)
      .mockRejectedValueOnce(new Error('la API está caída'))
      .mockResolvedValueOnce([solicitud(3, 'Ana Pérez')]);

    render(<ListaSolicitudes />);

    expect(await screen.findByRole('alert')).toHaveTextContent('la API está caída');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('heading', { name: 'Ana Pérez' })).toBeInTheDocument();
  });
});
