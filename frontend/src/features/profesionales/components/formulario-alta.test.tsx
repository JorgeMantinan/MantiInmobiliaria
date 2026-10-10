import type { ReactNode } from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
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

/** Slugs EXACTOS de `V4__profesionales.sql`. */
const GREMIOS = [
  { slug: 'fontanero', nombre: 'Fontanero' },
  { slug: 'electricista', nombre: 'Electricista' },
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
  await usuario.click(screen.getByRole('checkbox', { name: 'Fontanero' }));
}

async function enviar(usuario: ReturnType<typeof userEvent.setup>): Promise<void> {
  await usuario.click(screen.getByRole('button', { name: 'Registrarme como profesional' }));
}

/** Número de POST al alta (el GET del catálogo de gremios no cuenta). */
function contarPosts(): number {
  return vi.mocked(apiFetch).mock.calls.filter(([ruta]) => ruta === '/api/v1/profesionales/registro')
    .length;
}

beforeEach(() => {
  vi.clearAllMocks();
  conRutas({ '/api/v1/gremios': GREMIOS, '/api/v1/profesionales/registro': REGISTRO });
});

describe('FormularioAlta', () => {
  it('con_datos_validos_postea_el_alta_y_muestra_la_confirmacion', async () => {
    const usuario = userEvent.setup();
    render(<FormularioAlta />);
    await screen.findByRole('checkbox', { name: 'Fontanero' });

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
          gremios: ['fontanero'],
        },
      });
    });

    expect(
      await screen.findByText(
        'Registro recibido — validaremos tu cuenta y recibirás notificaciones',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('Fontanero')).toBeInTheDocument();
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
    await screen.findByRole('checkbox', { name: 'Fontanero' });

    await rellenar(usuario);
    await enviar(usuario);

    expect(await screen.findByRole('alert')).toHaveTextContent('Ese email ya está registrado.');
    expect(screen.getByLabelText('Email')).toBeInTheDocument();
  });

  it('sin_gremios_no_envia_el_registro_y_muestra_el_aviso', async () => {
    const usuario = userEvent.setup();
    render(<FormularioAlta />);
    await screen.findByRole('checkbox', { name: 'Fontanero' });

    await usuario.type(screen.getByLabelText('Nombre o razón social'), 'Luis Fontanero');
    await usuario.type(screen.getByLabelText('Email'), 'luis@ejemplo.com');
    await enviar(usuario);

    expect(await screen.findByText('Selecciona al menos un gremio.')).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledTimes(1);
  });

  it('con_un_429_muestra_el_mensaje_amigable_neutro_y_reactiva_el_boton', async () => {
    conRutas({
      '/api/v1/gremios': GREMIOS,
      '/api/v1/profesionales/registro': new ApiError(
        429,
        'Too Many Requests',
        'Rate limit superado',
      ),
    });
    const usuario = userEvent.setup();
    render(<FormularioAlta />);
    await screen.findByRole('checkbox', { name: 'Fontanero' });

    await rellenar(usuario);
    await enviar(usuario);

    expect(await screen.findByText('Demasiadas peticiones, inténtalo más tarde')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Registrarme como profesional' })).toBeEnabled();
  });

  it('el_telefono_de_los_30_caracteres_del_dto_del_backend', () => {
    render(<FormularioAlta />);

    // Paridad con `@Size(max = 30)` del DTO: el input no deja escribir más.
    expect(screen.getByLabelText('Teléfono (opcional)')).toHaveAttribute('maxLength', '30');
  });

  it('un_segundo_submit_con_la_peticion_en_vuelo_no_dispara_un_segundo_post', async () => {
    let confirmarAlta!: (valor: unknown) => void;
    vi.mocked(apiFetch).mockImplementation((ruta: string) => {
      if (ruta === '/api/v1/gremios') return Promise.resolve(GREMIOS);
      return new Promise<unknown>((resolver) => {
        confirmarAlta = resolver;
      });
    });
    const usuario = userEvent.setup();
    render(<FormularioAlta />);
    await screen.findByRole('checkbox', { name: 'Fontanero' });
    await rellenar(usuario);

    const boton = screen.getByRole('button', { name: 'Registrarme como profesional' });
    const formulario = boton.closest('form');
    if (formulario === null) throw new Error('no se encontró el formulario');

    fireEvent.submit(formulario);
    // El botón ya indica que la petición está en vuelo...
    expect(screen.getByRole('button', { name: 'Enviando registro…' })).toBeDisabled();
    // ...y un Enter repetido mientras sigue pendiente se descarta.
    fireEvent.submit(formulario);

    await waitFor(() => expect(apiFetch).toHaveBeenCalledTimes(2));
    expect(contarPosts()).toBe(1);

    await act(async () => {
      confirmarAlta(REGISTRO);
    });
    expect(
      await screen.findByText(
        'Registro recibido — validaremos tu cuenta y recibirás notificaciones',
      ),
    ).toBeInTheDocument();
    expect(contarPosts()).toBe(1);
  });

  it('con_un_400_mixto_pinta_los_conocidos_en_su_campo_y_conserva_los_desconocidos_en_el_global', async () => {
    conRutas({
      '/api/v1/gremios': GREMIOS,
      '/api/v1/profesionales/registro': new ApiError(400, 'Validación fallida', '2 campos', [
        { field: 'nombre', message: 'supera el máximo de caracteres' },
        { field: 'campoInexistente', message: 'campo no reconocido' },
      ]),
    });
    const usuario = userEvent.setup();
    render(<FormularioAlta />);
    await screen.findByRole('checkbox', { name: 'Fontanero' });

    await rellenar(usuario);
    await enviar(usuario);

    expect(await screen.findByText('supera el máximo de caracteres')).toBeInTheDocument();
    expect(screen.getByLabelText('Nombre o razón social')).toHaveAttribute('aria-invalid', 'true');
    // El field desconocido NO se descarta: sigue visible en el aviso global.
    expect(screen.getByText('campo no reconocido')).toHaveAttribute('aria-live', 'assertive');
  });
});
