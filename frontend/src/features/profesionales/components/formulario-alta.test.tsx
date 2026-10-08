import type { ReactNode } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FormularioAlta } from '@/features/profesionales/components/formulario-alta';
import { ApiError, apiFetch } from '@/lib/api';

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

const GREMIOS = [
  { slug: 'fontaneria', nombre: 'Fontanería' },
  { slug: 'electricidad', nombre: 'Electricidad' },
];

const REGISTRO = { id: 7, estado: 'PENDIENTE' as const };

/** Respuesta por ruta; un `Error` se lanza (así se simulan los ProblemDetail). */
function conRutas(rutas: Readonly<Record<string, unknown>>): void {
  vi.mocked(apiFetch).mockImplementation(async (ruta: string) => {
    const valor = rutas[ruta];
    if (valor instanceof Error) throw valor;
    if (valor !== undefined) return valor;
    throw new Error(`ruta inesperada: ${ruta}`);
  });
}

async function rellenar(usuario: ReturnType<typeof userEvent.setup>): Promise<void> {
  await usuario.type(screen.getByLabelText('Nombre o razón social'), 'Luis Fontanero');
  await usuario.type(screen.getByLabelText('Email'), 'luis@ejemplo.com');
  await usuario.click(screen.getByRole('checkbox', { name: 'Fontanería' }));
}

async function enviar(usuario: ReturnType<typeof userEvent.setup>): Promise<void> {
  await usuario.click(screen.getByRole('button', { name: 'Registrarme como profesional' }));
}

beforeEach(() => {
  vi.clearAllMocks();
  conRutas({ '/api/v1/gremios': GREMIOS, '/api/v1/profesionales/registro': REGISTRO });
});

describe('FormularioAlta', () => {
  it('con_datos_validos_postea_el_alta_y_muestra_la_confirmacion', async () => {
    const usuario = userEvent.setup();
    render(<FormularioAlta />);
    await screen.findByRole('checkbox', { name: 'Fontanería' });

    await rellenar(usuario);
    await usuario.type(screen.getByLabelText('Municipio (opcional)'), 'Vigo');
    await enviar(usuario);

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith('/api/v1/profesionales/registro', {
        method: 'POST',
        body: {
          nombre: 'Luis Fontanero',
          email: 'luis@ejemplo.com',
          municipio: 'Vigo',
          gremios: ['fontaneria'],
        },
      });
    });

    expect(
      await screen.findByText(
        'Registro recibido — validaremos tu cuenta y recibirás notificaciones',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('Fontanería')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a profesionales' })).toHaveAttribute(
      'href',
      '/profesionales',
    );
  });

  it('con_email_ya_registrado_muestra_el_conflicto_del_backend', async () => {
    conRutas({
      '/api/v1/gremios': GREMIOS,
      '/api/v1/profesionales/registro': new ApiError(409, 'Conflicto', 'El email ya existe'),
    });
    const usuario = userEvent.setup();
    render(<FormularioAlta />);
    await screen.findByRole('checkbox', { name: 'Fontanería' });

    await rellenar(usuario);
    await enviar(usuario);

    expect(await screen.findByRole('alert')).toHaveTextContent('Ese email ya está registrado.');
    expect(screen.getByLabelText('Email')).toBeInTheDocument();
  });

  it('sin_gremios_no_envia_el_registro_y_muestra_el_aviso', async () => {
    const usuario = userEvent.setup();
    render(<FormularioAlta />);
    await screen.findByRole('checkbox', { name: 'Fontanería' });

    await usuario.type(screen.getByLabelText('Nombre o razón social'), 'Luis Fontanero');
    await usuario.type(screen.getByLabelText('Email'), 'luis@ejemplo.com');
    await enviar(usuario);

    expect(await screen.findByText('Selecciona al menos un gremio.')).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledTimes(1);
  });
});
