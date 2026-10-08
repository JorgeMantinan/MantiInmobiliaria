import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  crearSolicitud,
  listarGremios,
  registrarProfesional,
} from '@/features/profesionales/api';
import { apiFetch } from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

const GREMIOS = [{ slug: 'fontaneria', nombre: 'Fontanería' }];

const SOLICITUD = {
  nombreContacto: 'Ana Pérez',
  emailContacto: 'ana@ejemplo.com',
  municipio: 'Oviedo',
  descripcion: 'Se ha roto el grifo del baño',
  gremios: ['fontaneria'],
} as const;

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(apiFetch).mockResolvedValue(GREMIOS);
});

describe('api de profesionales', () => {
  it('listar_gremios_hace_un_get_al_catalogo', async () => {
    const catalogo = await listarGremios();

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/gremios');
    expect(catalogo).toEqual(GREMIOS);
  });

  it('crear_solicitud_postea_el_contrato_con_body_y_metodo', async () => {
    const creada = { id: 42, estado: 'PENDIENTE', creadoEn: '2026-10-08T10:00:00Z' };
    vi.mocked(apiFetch).mockResolvedValue(creada);

    const resultado = await crearSolicitud(SOLICITUD);

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/solicitudes', {
      method: 'POST',
      body: SOLICITUD,
    });
    expect(resultado).toEqual(creada);
  });

  it('registrar_profesional_postea_su_contrato_y_devuelve_estado_pendiente', async () => {
    const alta = { nombre: 'Luis Fontanero', email: 'luis@ejemplo.com', gremios: ['fontaneria'] };
    vi.mocked(apiFetch).mockResolvedValue({ id: 7, estado: 'PENDIENTE' });

    const resultado = await registrarProfesional(alta);

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/profesionales/registro', {
      method: 'POST',
      body: alta,
    });
    expect(resultado.estado).toBe('PENDIENTE');
  });
});
