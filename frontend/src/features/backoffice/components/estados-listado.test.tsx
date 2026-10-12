import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { FilasError } from '@/features/backoffice/components/estados-listado';

describe('FilasError', () => {
  it('sin_bloquear_el_reintentar_queda_habilitado', () => {
    render(
      <FilasError
        titulo="No se han podido cargar los profesionales"
        mensaje="la API está caída"
        onReintentar={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeEnabled();
  });

  it('con_bloqueado_el_reintentar_queda_deshabilitado', () => {
    render(
      <FilasError
        titulo="No se han podido cargar los profesionales"
        mensaje="la API está caída"
        onReintentar={vi.fn()}
        bloqueado
      />,
    );

    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeDisabled();
  });
});
