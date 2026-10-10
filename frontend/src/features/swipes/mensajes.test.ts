import { afterEach, describe, expect, it, vi } from 'vitest';
import { MENSAJES, esSinSesion, mensajeDeError } from '@/features/swipes/mensajes';
import { ApiError } from '@/lib/api';

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('mensajeDeError de swipes', () => {
  it('en_desarrollo_devela_el_mensaje_interno_para_depurar', () => {
    vi.stubEnv('NODE_ENV', 'development');

    expect(mensajeDeError(new Error('detalle interno'), MENSAJES.deck)).toBe('detalle interno');
  });

  it('en_produccion_nunca_filtra_el_mensaje_interno', () => {
    vi.stubEnv('NODE_ENV', 'production');

    expect(mensajeDeError(new Error('detalle interno'), MENSAJES.deck)).toBe(MENSAJES.deck);
    expect(mensajeDeError('esto no es un Error', MENSAJES.deck)).toBe(MENSAJES.deck);
  });

  it('en_produccion_el_mensaje_del_backend_se_muestra_porque_es_generico', () => {
    vi.stubEnv('NODE_ENV', 'production');
    const fallo = new ApiError(500, 'Error interno', 'Fallo al cargar el deck');

    expect(mensajeDeError(fallo, MENSAJES.deck)).toBe('Error interno: Fallo al cargar el deck');
    expect(esSinSesion(new ApiError(401, 'No autorizado', 'Sesión caducada'))).toBe(true);
    expect(esSinSesion(fallo)).toBe(false);
  });
});
