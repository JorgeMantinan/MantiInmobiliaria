import type { ReactNode } from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FormularioSolicitud } from '@/features/profesionales/components/formulario-solicitud';
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
  { slug: 'carpintero', nombre: 'Carpintero' },
];

const CREADA = { id: 42, estado: 'NUEVA', creadoEn: '2026-10-08T10:00:00Z' };

/** Respuesta por ruta; un `Error` se lanza (así se simulan los ProblemDetail). */
function conRutas(rutas: Readonly<Record<string, unknown>>): void {
  vi.mocked(apiFetch).mockImplementation(async (ruta: string) => {
    const valor = rutas[ruta];
    if (valor instanceof Error) throw valor;
    if (valor !== undefined) return valor;
    throw new Error(`ruta inesperada: ${ruta}`);
  });
}

async function rellenarValido(usuario: ReturnType<typeof userEvent.setup>): Promise<void> {
  await usuario.type(screen.getByLabelText('Tu nombre'), 'Ana Pérez');
  await usuario.type(screen.getByLabelText('Email de contacto'), 'ana@ejemplo.com');
  await usuario.type(screen.getByLabelText('Municipio'), 'Oviedo');
  await usuario.type(
    screen.getByLabelText('Descripción del trabajo'),
    'Se ha roto el grifo del baño',
  );
  await usuario.click(screen.getByRole('checkbox', { name: 'Fontanero' }));
}

async function enviar(usuario: ReturnType<typeof userEvent.setup>): Promise<void> {
  await usuario.click(screen.getByRole('button', { name: 'Enviar solicitud' }));
}

/** Número de POST de la solicitud (el GET del catálogo de gremios no cuenta). */
function contarPosts(): number {
  return vi.mocked(apiFetch).mock.calls.filter(([ruta]) => ruta === '/api/v1/solicitudes').length;
}

beforeEach(() => {
  vi.clearAllMocks();
  conRutas({ '/api/v1/gremios': GREMIOS, '/api/v1/solicitudes': CREADA });
});

describe('FormularioSolicitud', () => {
  it('con_datos_validos_postea_el_contrato_y_muestra_la_confirmacion', async () => {
    const usuario = userEvent.setup();
    render(<FormularioSolicitud />);
    await screen.findByRole('checkbox', { name: 'Fontanero' });

    await rellenarValido(usuario);
    await usuario.click(screen.getByRole('checkbox', { name: 'Electricista' }));
    await enviar(usuario);

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith('/api/v1/solicitudes', {
        method: 'POST',
        body: {
          nombreContacto: 'Ana Pérez',
          emailContacto: 'ana@ejemplo.com',
          municipio: 'Oviedo',
          descripcion: 'Se ha roto el grifo del baño',
          gremios: ['fontanero', 'electricista'],
        },
      });
    });

    expect(
      await screen.findByText('Solicitud enviada, te contactarán los profesionales'),
    ).toBeInTheDocument();
    expect(screen.getByText('Fontanero')).toBeInTheDocument();
    expect(screen.getByText('Electricista')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toHaveAttribute('href', '/');
  });

  it('con_campos_invalidos_muestra_los_errores_sin_enviar', async () => {
    const usuario = userEvent.setup();
    render(<FormularioSolicitud />);
    await screen.findByRole('checkbox', { name: 'Fontanero' });

    await usuario.type(screen.getByLabelText('Email de contacto'), 'no-es-un-email');
    fireEvent.change(screen.getByLabelText('Municipio'), {
      target: { value: 'A'.repeat(121) },
    });
    await usuario.type(screen.getByLabelText('Descripción del trabajo'), 'corta');
    await enviar(usuario);

    expect(await screen.findByText('Escribe tu nombre.')).toBeInTheDocument();
    expect(screen.getByText('Escribe un email válido.')).toBeInTheDocument();
    expect(screen.getByText('El municipio no puede superar los 120 caracteres.')).toBeInTheDocument();
    expect(screen.getByText('Describe el trabajo con al menos 10 caracteres.')).toBeInTheDocument();
    expect(screen.getByText('Selecciona al menos un gremio.')).toBeInTheDocument();
    // Sólo el GET /api/v1/gremios: la solicitud NO se envía.
    expect(apiFetch).toHaveBeenCalledTimes(1);
  });

  it('con_un_400_del_backend_pinta_el_error_en_el_campo_y_no_en_el_aviso_global', async () => {
    conRutas({
      '/api/v1/gremios': GREMIOS,
      '/api/v1/solicitudes': new ApiError(400, 'Validación fallida', '1 campo inválido', [
        { field: 'descripcion', message: 'debe tener al menos 10 caracteres' },
      ]),
    });
    const usuario = userEvent.setup();
    render(<FormularioSolicitud />);
    await screen.findByRole('checkbox', { name: 'Fontanero' });

    await rellenarValido(usuario);
    await enviar(usuario);

    const descripcion = screen.getByLabelText('Descripción del trabajo');
    expect(await screen.findByText('debe tener al menos 10 caracteres')).toBeInTheDocument();
    // El error vive EN el campo: marcado y referenciado por `aria-describedby`.
    expect(descripcion).toHaveAttribute('aria-invalid', 'true');
    const referencias = (descripcion.getAttribute('aria-describedby') ?? '').split(' ');
    // Ayuda y error conviven en `aria-describedby` (item de revisión de campo-textarea).
    expect(referencias).toEqual(
      expect.arrayContaining(['solicitud-descripcion-ayuda', 'solicitud-descripcion-error']),
    );
    const errorCampo = document.getElementById('solicitud-descripcion-error');
    expect(errorCampo).toHaveAttribute('role', 'alert');
    expect(errorCampo).toHaveTextContent('debe tener al menos 10 caracteres');
    // El aviso global es sólo fallback: aquí NO aparece.
    expect(document.querySelector('[aria-live="assertive"]')).toBeNull();
  });

  it('con_un_400_sin_campos_conocidos_usa_el_aviso_global', async () => {
    conRutas({
      '/api/v1/gremios': GREMIOS,
      '/api/v1/solicitudes': new ApiError(400, 'Validación fallida', 'Error de validación', [
        { field: 'campoInexistente', message: 'campo no reconocido' },
      ]),
    });
    const usuario = userEvent.setup();
    render(<FormularioSolicitud />);
    await screen.findByRole('checkbox', { name: 'Fontanero' });

    await rellenarValido(usuario);
    await enviar(usuario);

    const global = await screen.findByText('campo no reconocido');
    expect(global).toHaveAttribute('aria-live', 'assertive');
    expect(screen.getByLabelText('Descripción del trabajo')).toHaveAttribute(
      'aria-invalid',
      'false',
    );
  });

  it('con_un_429_muestra_el_mensaje_amigable_y_reactiva_el_boton', async () => {
    conRutas({
      '/api/v1/gremios': GREMIOS,
      '/api/v1/solicitudes': new ApiError(429, 'Too Many Requests', 'Rate limit superado'),
    });
    const usuario = userEvent.setup();
    render(<FormularioSolicitud />);
    await screen.findByRole('checkbox', { name: 'Fontanero' });

    await rellenarValido(usuario);
    await enviar(usuario);

    expect(await screen.findByText('Demasiadas peticiones, inténtalo más tarde')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Enviar solicitud' })).toBeEnabled();
  });

  it('mientras_cargan_los_gremios_muestra_el_esqueleto_y_bloquea_el_envio', () => {
    vi.mocked(apiFetch).mockReturnValue(new Promise(() => {}));

    render(<FormularioSolicitud />);

    expect(screen.getByRole('status')).toHaveTextContent('Cargando gremios…');
    expect(screen.getByRole('button', { name: 'Enviar solicitud' })).toBeDisabled();
  });

  it('si_la_api_de_gremios_falla_avisa_y_usa_la_lista_de_reserva', async () => {
    conRutas({
      '/api/v1/gremios': new ApiError(0, 'Error de conexión', 'Sin conexión con la API.'),
    });
    render(<FormularioSolicitud />);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No se han podido cargar los gremios; mostramos la lista de referencia.',
    );
    expect(screen.getByRole('checkbox', { name: 'Fontanero' })).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Arquitecto' })).toBeInTheDocument();
  });

  it('el_telefono_de_los_30_caracteres_del_dto_del_backend', () => {
    render(<FormularioSolicitud />);

    // Paridad con `@Size(max = 30)` del DTO: el input no deja escribir más.
    expect(screen.getByLabelText('Teléfono (opcional)')).toHaveAttribute('maxLength', '30');
  });

  it('un_segundo_submit_con_la_peticion_en_vuelo_no_dispara_un_segundo_post', async () => {
    let confirmarSolicitud!: (valor: unknown) => void;
    vi.mocked(apiFetch).mockImplementation((ruta: string) => {
      if (ruta === '/api/v1/gremios') return Promise.resolve(GREMIOS);
      return new Promise<unknown>((resolver) => {
        confirmarSolicitud = resolver;
      });
    });
    const usuario = userEvent.setup();
    render(<FormularioSolicitud />);
    await screen.findByRole('checkbox', { name: 'Fontanero' });
    await rellenarValido(usuario);

    const boton = screen.getByRole('button', { name: 'Enviar solicitud' });
    const formulario = boton.closest('form');
    if (formulario === null) throw new Error('no se encontró el formulario');

    fireEvent.submit(formulario);
    // El botón ya indica que la petición está en vuelo...
    expect(screen.getByRole('button', { name: 'Enviando solicitud…' })).toBeDisabled();
    // ...y un Enter repetido mientras sigue pendiente se descarta.
    fireEvent.submit(formulario);

    await waitFor(() => expect(apiFetch).toHaveBeenCalledTimes(2));
    expect(contarPosts()).toBe(1);

    await act(async () => {
      confirmarSolicitud(CREADA);
    });
    expect(
      await screen.findByText('Solicitud enviada, te contactarán los profesionales'),
    ).toBeInTheDocument();
    expect(contarPosts()).toBe(1);
  });

  it('con_un_400_mixto_pinta_los_conocidos_en_su_campo_y_conserva_los_desconocidos_en_el_global', async () => {
    conRutas({
      '/api/v1/gremios': GREMIOS,
      '/api/v1/solicitudes': new ApiError(400, 'Validación fallida', '2 campos', [
        { field: 'descripcion', message: 'debe tener al menos 10 caracteres' },
        { field: 'campoInexistente', message: 'campo no reconocido' },
      ]),
    });
    const usuario = userEvent.setup();
    render(<FormularioSolicitud />);
    await screen.findByRole('checkbox', { name: 'Fontanero' });

    await rellenarValido(usuario);
    await enviar(usuario);

    expect(await screen.findByText('debe tener al menos 10 caracteres')).toBeInTheDocument();
    expect(screen.getByLabelText('Descripción del trabajo')).toHaveAttribute(
      'aria-invalid',
      'true',
    );
    // El field desconocido NO se descarta: sigue visible en el aviso global.
    expect(screen.getByText('campo no reconocido')).toHaveAttribute('aria-live', 'assertive');
  });
});
