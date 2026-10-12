import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { FilaProfesional } from '@/features/backoffice/components/fila-profesional';
import type { ProfesionalAdmin } from '@/features/backoffice/types';

const luis: ProfesionalAdmin = {
  id: 7,
  nombre: 'Luis Fontanero',
  email: 'luis@ejemplo.com',
  telefono: '600000001',
  municipio: 'Oviedo',
  gremios: ['fontanero'],
  estado: 'PENDIENTE',
  creadoEn: '2026-10-01T10:00:00Z',
};

describe('FilaProfesional — foco de la confirmación', () => {
  it('al_confirmar_el_foco_vuelve_al_gatillo_que_abrio_la_confirmacion', async () => {
    const onCambio = vi.fn();
    render(<FilaProfesional profesional={luis} ocupado={false} onCambio={onCambio} />);

    const usuario = userEvent.setup();
    const gatillo = screen.getByRole('button', { name: 'Aprobar' });
    await usuario.click(gatillo);
    expect(document.activeElement).not.toBe(gatillo);

    await usuario.click(screen.getByRole('button', { name: 'Sí, aprobar' }));

    expect(onCambio).toHaveBeenCalledWith(luis, 'ACTIVO');
    expect(screen.queryByRole('group', { name: 'Confirmar: Aprobar' })).not.toBeInTheDocument();
    // El gatillo sigue montado (el estado lo decide el padre): recupera el foco.
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Aprobar' }));
  });

  it('al_cancelar_el_foco_vuelve_al_gatillo_que_abrio_la_confirmacion', async () => {
    render(<FilaProfesional profesional={luis} ocupado={false} onCambio={vi.fn()} />);

    const usuario = userEvent.setup();
    const gatillo = screen.getByRole('button', { name: 'Rechazar' });
    await usuario.click(gatillo);

    await usuario.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('group', { name: 'Confirmar: Rechazar' })).not.toBeInTheDocument();
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Rechazar' }));
  });
});
