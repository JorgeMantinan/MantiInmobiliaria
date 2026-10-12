import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { FilaInmueble } from '@/features/backoffice/components/fila-inmueble';
import type { InmuebleResumen } from '@/features/inmuebles/types';

const piso: InmuebleResumen = {
  id: 12,
  titulo: 'Piso céntrico con ascensor',
  precio: 245000,
  tipo: 'PISO',
  estado: 'VENTA',
  superficieM2: 96,
  habitaciones: 3,
  banios: 1,
  municipio: 'Madrid',
  provincia: 'Madrid',
  imagenUrl: null,
  destacado: true,
  lat: 40.4168,
  lng: -3.7038,
  createdAt: '2026-10-01T10:00:00Z',
};

describe('FilaInmueble — borrado en dos pasos', () => {
  it('al_eliminar_en_dos_pasos_llama_a_onEliminar_con_el_inmueble_y_devuelve_el_foco', async () => {
    const onEliminar = vi.fn();
    render(<FilaInmueble inmueble={piso} ocupado={false} onEliminar={onEliminar} />);

    const usuario = userEvent.setup();
    const gatillo = screen.getByRole('button', { name: 'Eliminar' });
    await usuario.click(gatillo);

    // Sólo el primer paso: todavía NO se borra.
    expect(onEliminar).not.toHaveBeenCalled();
    expect(screen.getByRole('group', { name: 'Confirmar: Eliminar' })).toBeInTheDocument();

    await usuario.click(screen.getByRole('button', { name: 'Sí, eliminar' }));

    expect(onEliminar).toHaveBeenCalledWith(piso);
    expect(screen.queryByRole('group', { name: 'Confirmar: Eliminar' })).not.toBeInTheDocument();
    // El gatillo sigue montado (el estado lo decide el padre): recupera el foco.
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Eliminar' }));
  });

  it('al_cancelar_no_se_borra_y_el_foco_vuelve_al_gatillo', async () => {
    const onEliminar = vi.fn();
    render(<FilaInmueble inmueble={piso} ocupado={false} onEliminar={onEliminar} />);

    const usuario = userEvent.setup();
    await usuario.click(screen.getByRole('button', { name: 'Eliminar' }));
    await usuario.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(onEliminar).not.toHaveBeenCalled();
    expect(screen.queryByRole('group', { name: 'Confirmar: Eliminar' })).not.toBeInTheDocument();
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Eliminar' }));
  });

  it('muestra_los_badges_de_estado_y_destacado_y_el_enlace_de_edicion', () => {
    render(<FilaInmueble inmueble={piso} ocupado={false} onEliminar={vi.fn()} />);

    expect(screen.getByText('En venta')).toBeInTheDocument();
    expect(screen.getByText('Destacado')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Editar' })).toHaveAttribute(
      'href',
      '/admin/inmuebles/12',
    );
    expect(screen.getByRole('button', { name: 'Eliminar' })).toBeEnabled();
  });

  it('un_borrado_en_vuelo_deshabilita_el_gatillo_de_eliminar', () => {
    render(<FilaInmueble inmueble={piso} ocupado={true} onEliminar={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Eliminar' })).toBeDisabled();
  });
});
