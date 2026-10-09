import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { BarraFiltros } from '@/features/inmuebles/components/barra-filtros';
import type { FiltrosInmuebles } from '@/features/inmuebles/types';

type Render = Readonly<{
  onAplicar: ReturnType<typeof vi.fn>;
  onLimpiar: ReturnType<typeof vi.fn>;
}>;

function pintar(filtros: FiltrosInmuebles): Render {
  const onAplicar = vi.fn();
  const onLimpiar = vi.fn();
  render(<BarraFiltros filtros={filtros} onAplicar={onAplicar} onLimpiar={onLimpiar} />);
  return { onAplicar, onLimpiar };
}

describe('BarraFiltros', () => {
  it('ante_un_rango_de_precio_invertido_no_navega_y_muestra_el_aviso', async () => {
    const usuario = userEvent.setup();
    const { onAplicar } = pintar({});

    await usuario.type(screen.getByLabelText('Precio mínimo (€)'), '300000');
    await usuario.type(screen.getByLabelText('Precio máximo (€)'), '100000');
    await usuario.click(screen.getByRole('button', { name: 'Aplicar filtros' }));

    // No se emite la query: el backend respondería 400 con este rango.
    expect(onAplicar).not.toHaveBeenCalled();
    expect(screen.getByRole('status')).toHaveTextContent(
      'El precio mínimo no puede superar al máximo.',
    );
  });

  it('al_aplicar_conserva_los_filtros_vigentes_que_no_estan_en_el_formulario', async () => {
    const usuario = userEvent.setup();
    const { onAplicar } = pintar({ destacado: true });

    await usuario.type(screen.getByLabelText('Municipio'), 'Madrid');
    await usuario.click(screen.getByRole('button', { name: 'Aplicar filtros' }));

    expect(onAplicar).toHaveBeenCalledWith(
      expect.objectContaining({ destacado: true, municipio: 'Madrid' }),
    );
  });
});
