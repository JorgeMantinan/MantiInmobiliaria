import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ConmutadorVista } from '@/features/inmuebles/components/conmutador-vista';

describe('ConmutadorVista', () => {
  it('marca_con_aria_pressed_la_vista_activa', () => {
    render(<ConmutadorVista vista="lista" onCambiar={() => {}} />);

    expect(screen.getByRole('button', { name: 'Lista' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'Mapa' })).toHaveAttribute('aria-pressed', 'false');
  });

  it('refleja_el_mapa_como_vista_activa', () => {
    render(<ConmutadorVista vista="mapa" onCambiar={() => {}} />);

    expect(screen.getByRole('button', { name: 'Mapa' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'Lista' })).toHaveAttribute('aria-pressed', 'false');
  });

  it('al_pulsar_mapa_pide_cambiar_a_la_vista_de_mapa', async () => {
    const usuario = userEvent.setup();
    const onCambiar = vi.fn();
    render(<ConmutadorVista vista="lista" onCambiar={onCambiar} />);

    await usuario.click(screen.getByRole('button', { name: 'Mapa' }));

    expect(onCambiar).toHaveBeenCalledWith('mapa');
  });

  it('al_pulsar_lista_pide_volver_a_la_vista_de_lista', async () => {
    const usuario = userEvent.setup();
    const onCambiar = vi.fn();
    render(<ConmutadorVista vista="mapa" onCambiar={onCambiar} />);

    await usuario.click(screen.getByRole('button', { name: 'Lista' }));

    expect(onCambiar).toHaveBeenCalledWith('lista');
  });
});
