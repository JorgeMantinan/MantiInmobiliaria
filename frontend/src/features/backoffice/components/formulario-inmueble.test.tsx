import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FormularioInmueble } from '@/features/backoffice/components/formulario-inmueble';
import type { InmuebleDetalle } from '@/features/inmuebles/types';
import { apiFetch, ApiError } from '@/lib/api';

const navegacion = vi.hoisted(() => ({ push: vi.fn() }));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: navegacion.push, replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(''),
}));

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

const DETALLE: InmuebleDetalle = {
  id: 7,
  titulo: 'Piso céntrico con ascensor',
  descripcion: 'Luminoso piso reformado.',
  precio: 245000,
  superficieM2: 96,
  habitaciones: 3,
  banios: 1,
  tipo: 'PISO',
  estado: 'VENTA',
  direccion: 'Calle Mayor 1, 3º B',
  codigoPostal: '28013',
  municipio: 'Madrid',
  provincia: 'Madrid',
  codigoIne: '28079',
  lat: 40.4168,
  lng: -3.7038,
  imagenUrl: 'https://picsum.photos/seed/inm1/800/600',
  destacado: false,
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-02T10:00:00Z',
};

/** Rellena el form mínimo VÁLIDO (lo demás opcional). */
async function rellenarValido(usuario: ReturnType<typeof userEvent.setup>): Promise<void> {
  await usuario.type(screen.getByLabelText('Título del anuncio'), 'Ático con terraza');
  await usuario.type(screen.getByLabelText('Precio en euros'), '320000.50');
  await usuario.selectOptions(screen.getByLabelText('Tipo de inmueble'), 'ATICO');
  await usuario.selectOptions(screen.getByLabelText('Estado comercial'), 'ALQUILER');
  await usuario.type(screen.getByLabelText('Dirección completa'), 'Calle Sol 2');
  await usuario.type(screen.getByLabelText('Municipio'), 'Madrid');
  await usuario.type(screen.getByLabelText('Provincia'), 'Madrid');
  await usuario.type(screen.getByLabelText(/Latitud/), '40.4168');
  await usuario.type(screen.getByLabelText(/Longitud/), '-3.7038');
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('FormularioInmueble — alta (POST)', () => {
  it('envia_el_payload_completo_y_navega_a_la_lista_en_el_exito', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ id: 42 });
    const usuario = userEvent.setup();
    render(<FormularioInmueble inicial={null} />);

    await rellenarValido(usuario);
    await usuario.click(screen.getByRole('button', { name: 'Crear inmueble' }));

    await waitFor(() => expect(apiFetch).toHaveBeenCalledTimes(1));
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/admin/inmuebles', {
      method: 'POST',
      body: {
        titulo: 'Ático con terraza',
        descripcion: null,
        precio: 320000.5,
        superficieM2: null,
        habitaciones: null,
        banios: null,
        tipo: 'ATICO',
        estado: 'ALQUILER',
        direccion: 'Calle Sol 2',
        codigoPostal: null,
        municipio: 'Madrid',
        provincia: 'Madrid',
        lat: 40.4168,
        lng: -3.7038,
        imagenUrl: null,
        destacado: false,
      },
    });
    expect(navegacion.push).toHaveBeenCalledWith('/admin/inmuebles');
  });

  it('la_validacion_client_bloquea_el_envio_y_pinta_los_errores_de_campo', async () => {
    const usuario = userEvent.setup();
    render(<FormularioInmueble inicial={null} />);

    // Sólo el título: todo lo demás obligatorio falta.
    await usuario.type(screen.getByLabelText('Título del anuncio'), 'X');
    await usuario.click(screen.getByRole('button', { name: 'Crear inmueble' }));

    expect(apiFetch).not.toHaveBeenCalled();
    expect(screen.getByText('Escribe el precio.')).toBeInTheDocument();
    expect(screen.getByText(/Selecciona el tipo/)).toBeInTheDocument();
    expect(screen.getByText('Escribe la dirección.')).toBeInTheDocument();
    expect(navegacion.push).not.toHaveBeenCalled();
  });

  it('un_400_con_errors_pinta_el_mensaje_en_el_campo_correspondiente', async () => {
    vi.mocked(apiFetch).mockRejectedValue(
      new ApiError(400, 'Validación fallida', '2 campos inválidos', [
        { field: 'precio', message: 'debe ser mayor que 0.01' },
        { field: 'lat', message: 'debe estar entre 27.0 y 44.0' },
      ]),
    );
    const usuario = userEvent.setup();
    render(<FormularioInmueble inicial={null} />);

    await rellenarValido(usuario);
    await usuario.click(screen.getByRole('button', { name: 'Crear inmueble' }));

    expect(await screen.findByText('debe ser mayor que 0.01')).toBeInTheDocument();
    expect(screen.getByText('debe estar entre 27.0 y 44.0')).toBeInTheDocument();
    expect(navegacion.push).not.toHaveBeenCalled();
  });

  it('un_401_o_403_muestra_el_aviso_global_de_sesion_o_permisos', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(401, 'Sin token', 'No autenticado'),
    );
    const usuario = userEvent.setup();
    render(<FormularioInmueble inicial={null} />);

    await rellenarValido(usuario);
    await usuario.click(screen.getByRole('button', { name: 'Crear inmueble' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/sesión ha caducado/);
  });

  it('un_403_muestra_que_la_cuenta_no_tiene_permisos', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(403, 'Prohibido', 'Token sin rol ADMIN'),
    );
    const usuario = userEvent.setup();
    render(<FormularioInmueble inicial={null} />);

    await rellenarValido(usuario);
    await usuario.click(screen.getByRole('button', { name: 'Crear inmueble' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/no tiene permisos/);
  });
});

describe('FormularioInmueble — edición (PUT)', () => {
  it('precarga_los_valores_y_envia_un_put_al_id_con_el_cambio_de_estado', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ ...DETALLE, estado: 'ALQUILER' });
    const usuario = userEvent.setup();
    render(<FormularioInmueble inicial={DETALLE} />);

    // Precarga desde el detalle.
    expect(screen.getByLabelText('Título del anuncio')).toHaveValue(DETALLE.titulo);
    expect(screen.getByLabelText('Precio en euros')).toHaveValue(245000);
    expect(screen.getByLabelText('Tipo de inmueble')).toHaveValue('PISO');
    expect(screen.getByLabelText('Estado comercial')).toHaveValue('VENTA');
    // El codigoIne NO se edita (el request del backend no lo admite).
    expect(screen.getByText('28079')).toBeInTheDocument();

    await usuario.clear(screen.getByLabelText('Precio en euros'));
    await usuario.type(screen.getByLabelText('Precio en euros'), '230000');
    await usuario.selectOptions(screen.getByLabelText('Estado comercial'), 'ALQUILER');
    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await waitFor(() => expect(apiFetch).toHaveBeenCalledTimes(1));
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/admin/inmuebles/7', {
      method: 'PUT',
      body: expect.objectContaining({
        titulo: DETALLE.titulo,
        precio: 230000,
        estado: 'ALQUILER',
        tipo: 'PISO',
        municipio: 'Madrid',
        codigoPostal: '28013',
        imagenUrl: DETALLE.imagenUrl,
        destacado: false,
      }),
    });
    expect(navegacion.push).toHaveBeenCalledWith('/admin/inmuebles');
  });

  it('un_409_de_un_inmueble_ya_eliminado_muestra_el_aviso_de_conflicto', async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(
      new ApiError(409, 'Conflicto', 'El inmueble 7 ya está eliminado'),
    );
    const usuario = userEvent.setup();
    render(<FormularioInmueble inicial={DETALLE} />);

    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/ya estaba eliminado/);
    expect(navegacion.push).not.toHaveBeenCalled();
  });
});
