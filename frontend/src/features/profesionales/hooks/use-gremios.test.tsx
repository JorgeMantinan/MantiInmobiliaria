import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useGremios } from '@/features/profesionales/hooks/use-gremios';
import { MENSAJES } from '@/features/profesionales/mensajes';
import { GREMIOS_DE_RESERVA } from '@/features/profesionales/types';
import { ApiError, apiFetch } from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

beforeEach(() => {
  vi.clearAllMocks();
});

describe('useGremios', () => {
  it('con_la_api_ok_usa_el_catalogo_del_servidor_sin_aviso', async () => {
    const servidor = [{ slug: 'gasfiteria', nombre: 'Gasfitería' }];
    vi.mocked(apiFetch).mockResolvedValue(servidor);

    const { result } = renderHook(() => useGremios());
    expect(result.current.cargando).toBe(true);

    await waitFor(() => expect(result.current.cargando).toBe(false));

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/gremios');
    expect(result.current.gremios).toEqual(servidor);
    expect(result.current.aviso).toBeNull();
  });

  it('si_la_api_falla_usa_la_lista_de_reserva_de_12_y_avisa', async () => {
    vi.mocked(apiFetch).mockRejectedValue(
      new ApiError(0, 'Error de conexión', 'No se ha podido contactar con el servidor.'),
    );

    const { result } = renderHook(() => useGremios());
    await waitFor(() => expect(result.current.cargando).toBe(false));

    expect(result.current.gremios).toEqual(GREMIOS_DE_RESERVA);
    expect(result.current.gremios).toHaveLength(12);
    expect(result.current.aviso).toBe(MENSAJES.gremiosReserva);
  });
});
