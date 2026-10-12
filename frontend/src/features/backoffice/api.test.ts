import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  actualizarInmueble,
  cambiarEstadoProfesional,
  crearInmueble,
  eliminarInmueble,
  listarProfesionales,
  listarSolicitudes,
} from '@/features/backoffice/api';
import type { InmuebleEnvio, ProfesionalAdmin } from '@/features/backoffice/types';
import { apiFetch } from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

const PROFESIONAL: ProfesionalAdmin = {
  id: 7,
  nombre: 'Luis Fontanero',
  email: 'luis@ejemplo.com',
  telefono: '600000001',
  municipio: 'Oviedo',
  gremios: ['fontanero'],
  estado: 'PENDIENTE',
  creadoEn: '2026-10-01T10:00:00Z',
};

const INMUEBLE_ENVIO: InmuebleEnvio = {
  titulo: 'Piso céntrico',
  descripcion: null,
  precio: 245000,
  superficieM2: 96,
  habitaciones: 3,
  banios: 1,
  tipo: 'PISO',
  estado: 'VENTA',
  direccion: 'Calle Mayor 1',
  codigoPostal: '28013',
  municipio: 'Madrid',
  provincia: 'Madrid',
  lat: 40.4168,
  lng: -3.7038,
  imagenUrl: null,
  destacado: false,
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(apiFetch).mockResolvedValue([]);
});

describe('api del backoffice', () => {
  it('listar_profesionales_sin_estado_pide_la_ruta_plana', async () => {
    vi.mocked(apiFetch).mockResolvedValue([PROFESIONAL]);

    const filas = await listarProfesionales();

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/admin/profesionales');
    expect(filas).toEqual([PROFESIONAL]);
  });

  it('listar_profesionales_con_estado_filtra_por_query', async () => {
    await listarProfesionales('ACTIVO');

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/admin/profesionales?estado=ACTIVO');
  });

  it('cambiar_estado_hace_un_patch_al_id_con_el_body_del_contrato', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ id: 7, estado: 'ACTIVO' });

    const respuesta = await cambiarEstadoProfesional(7, 'ACTIVO');

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/admin/profesionales/7', {
      method: 'PATCH',
      body: { estado: 'ACTIVO' },
    });
    expect(respuesta).toEqual({ id: 7, estado: 'ACTIVO' });
  });

  it('listar_solicitudes_pide_el_listado_admin', async () => {
    await listarSolicitudes();

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/admin/solicitudes');
  });

  it('crear_inmueble_hace_un_post_a_la_ruta_admin_con_el_payload', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ id: 42, ...INMUEBLE_ENVIO });

    const respuesta = await crearInmueble(INMUEBLE_ENVIO);

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/admin/inmuebles', {
      method: 'POST',
      body: INMUEBLE_ENVIO,
    });
    expect(respuesta).toEqual({ id: 42, ...INMUEBLE_ENVIO });
  });

  it('actualizar_inmueble_hace_un_put_al_id_con_el_payload', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ id: 7, ...INMUEBLE_ENVIO });

    await actualizarInmueble(7, INMUEBLE_ENVIO);

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/admin/inmuebles/7', {
      method: 'PUT',
      body: INMUEBLE_ENVIO,
    });
  });

  it('eliminar_inmueble_hace_un_delete_al_id', async () => {
    vi.mocked(apiFetch).mockResolvedValue(undefined);

    await eliminarInmueble(7);

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/admin/inmuebles/7', { method: 'DELETE' });
  });
});
