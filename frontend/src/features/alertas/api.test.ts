import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  actualizarAlerta,
  crearAlerta,
  eliminarAlerta,
  listarAlertas,
} from '@/features/alertas/api';
import { apiFetch } from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(apiFetch).mockResolvedValue([]);
});

describe('api de alertas', () => {
  it('listar_haceUnGetASuRuta', async () => {
    await listarAlertas();

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/alertas');
  });

  it('crear_posteaElNombreYLosFiltrosTalCual', async () => {
    const filtros = { estado: 'VENTA' as const, precioMin: 100000, municipio: 'Madrid' };

    await crearAlerta('Pisos Madrid', filtros);

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/alertas', {
      method: 'POST',
      body: { nombre: 'Pisos Madrid', filtros },
    });
  });

  it('actualizar_haceUnPatchConElBodyParcial', async () => {
    await actualizarAlerta(7, { activa: false });

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/alertas/7', {
      method: 'PATCH',
      body: { activa: false },
    });
  });

  it('eliminar_haceUnDeleteASuRuta', async () => {
    await eliminarAlerta(9);

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/alertas/9', { method: 'DELETE' });
  });

  it('rechazaIdsInvalidos_sinLlamarALaApi', async () => {
    await expect(actualizarAlerta(0, { activa: true })).rejects.toThrow('inválido');
    await expect(eliminarAlerta(1.5)).rejects.toThrow('inválido');
    await expect(eliminarAlerta(Number.NaN)).rejects.toThrow('inválido');
    expect(apiFetch).not.toHaveBeenCalled();
  });
});
