import { beforeEach, describe, expect, it, vi } from 'vitest';
import { eliminarFavorito, listarFavoritos, obtenerDeck, registrarSwipe } from '@/features/swipes/api';
import { apiFetch } from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

const PAGINA_VACIA = { content: [], nextCursor: null };

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(apiFetch).mockResolvedValue(PAGINA_VACIA);
});

describe('api de swipes', () => {
  it('registrar_swipe_postea_el_body_fijado_en_el_contrato', async () => {
    await registrarSwipe(42, 'LIKE');

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/swipes', {
      method: 'POST',
      body: { inmuebleId: 42, decision: 'LIKE' },
    });
  });

  it('el_deck_y_los_favoritos_llevan_limit_y_cursor_opcional', async () => {
    await obtenerDeck();
    await listarFavoritos();
    await obtenerDeck('abc/def+');

    expect(apiFetch).toHaveBeenNthCalledWith(1, '/api/v1/swipes/deck?limit=20');
    expect(apiFetch).toHaveBeenNthCalledWith(2, '/api/v1/swipes/favoritos?limit=20');
    expect(apiFetch).toHaveBeenNthCalledWith(
      3,
      `/api/v1/swipes/deck?limit=20&cursor=${encodeURIComponent('abc/def+')}`,
    );
  });

  it('eliminar_favorito_hace_un_delete_a_su_ruta', async () => {
    await eliminarFavorito(7);

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/swipes/favoritos/7', { method: 'DELETE' });
  });

  it('rechaza_ids_de_inmueble_invalidos_sin_llamar_a_la_api', async () => {
    await expect(registrarSwipe(1.5, 'LIKE')).rejects.toThrow('entero positivo');
    await expect(registrarSwipe(-3, 'NOPE')).rejects.toThrow('entero positivo');
    await expect(eliminarFavorito(0)).rejects.toThrow('entero positivo');
    await expect(eliminarFavorito(Number.NaN)).rejects.toThrow('entero positivo');
    expect(apiFetch).not.toHaveBeenCalled();
  });
});
