import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { SelectorGremios } from '@/features/profesionales/components/selector-gremios';
import type { EstadoGremios } from '@/features/profesionales/hooks/use-gremios';
import type { Gremio } from '@/features/profesionales/types';
import { GREMIOS_MAXIMOS } from '@/features/profesionales/validacion';

const GREMIOS: readonly Gremio[] = [
  { slug: 'fontaneria', nombre: 'Fontanería' },
  { slug: 'electricidad', nombre: 'Electricidad' },
  { slug: 'carpinteria', nombre: 'Carpintería' },
];

const DIEZ: readonly Gremio[] = Array.from({ length: 10 }, (_, indice) => ({
  slug: `gremio-${indice + 1}`,
  nombre: `Gremio ${indice + 1}`,
}));

const LISTO: EstadoGremios = { gremios: GREMIOS, cargando: false, aviso: null };

type PropietariaProps = Readonly<{
  gremios: readonly Gremio[];
  inicial?: readonly string[];
}>;

/** Estado controlado de verdad: el selector sólo notifica cambios. */
function Propietaria({ gremios, inicial = [] }: PropietariaProps) {
  const [seleccion, setSeleccion] = useState<readonly string[]>(inicial);
  return (
    <SelectorGremios
      id="gremio"
      estado={{ gremios, cargando: false, aviso: null }}
      seleccion={seleccion}
      onCambio={setSeleccion}
    />
  );
}

describe('SelectorGremios', () => {
  it('permite_seleccionar_varios_gremios_y_actualiza_el_contador', async () => {
    const usuario = userEvent.setup();
    render(<Propietaria gremios={GREMIOS} />);

    await usuario.click(screen.getByRole('checkbox', { name: 'Fontanería' }));
    await usuario.click(screen.getByRole('checkbox', { name: 'Electricidad' }));

    expect(screen.getByRole('checkbox', { name: 'Fontanería' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Electricidad' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Carpintería' })).not.toBeChecked();
    expect(screen.getByRole('status')).toHaveTextContent('2 de 8 gremios seleccionados.');
  });

  it('con_cero_seleccionados_muestra_el_aviso_de_gremio_minimo', () => {
    render(<Propietaria gremios={GREMIOS} />);

    expect(screen.getByRole('status')).toHaveTextContent('Selecciona al menos 1 gremio.');
    expect(screen.getByRole('checkbox', { name: 'Fontanería' })).not.toBeChecked();
  });

  it('al_llegar_al_limite_de_ocho_deshabilita_los_demas_sin_bloquear_los_marcados', async () => {
    const usuario = userEvent.setup();
    const ocho = DIEZ.slice(0, GREMIOS_MAXIMOS).map((gremio) => gremio.slug);
    render(<Propietaria gremios={DIEZ} inicial={ocho} />);

    expect(screen.getByRole('checkbox', { name: 'Gremio 9' })).toBeDisabled();
    expect(screen.getByRole('checkbox', { name: 'Gremio 10' })).toBeDisabled();
    expect(screen.getByRole('status')).toHaveTextContent(`Máximo ${GREMIOS_MAXIMOS} gremios`);

    await usuario.click(screen.getByRole('checkbox', { name: 'Gremio 1' }));

    expect(screen.getByRole('checkbox', { name: 'Gremio 1' })).not.toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Gremio 9' })).toBeEnabled();
  });

  it('con_error_de_validacion_muestra_un_alert_en_lugar_del_contador', () => {
    render(
      <SelectorGremios
        id="gremio"
        estado={LISTO}
        seleccion={[]}
        onCambio={() => undefined}
        error="Selecciona al menos un gremio."
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Selecciona al menos un gremio.');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('mientras_carga_pinta_su_esqueleto_en_lugar_de_la_rejilla', () => {
    render(
      <SelectorGremios
        id="gremio"
        estado={{ gremios: [], cargando: true, aviso: null }}
        seleccion={[]}
        onCambio={() => undefined}
      />,
    );

    expect(screen.getByRole('status')).toHaveTextContent('Cargando gremios…');
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });
});
