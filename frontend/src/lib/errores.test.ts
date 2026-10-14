import { afterEach, describe, expect, it, vi } from 'vitest';
import { esSinSesion, mensajeDeError } from '@/lib/errores';
import { ApiError } from '@/lib/api';

const POR_DEFECTO = 'No se han podido cargar los datos.';

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('mensajeDeError de lib/errores', () => {
  it('en_desarrollo_devela_el_mensaje_interno_para_depurar', () => {
    vi.stubEnv('NODE_ENV', 'development');

    expect(mensajeDeError(new Error('detalle interno'), POR_DEFECTO)).toBe('detalle interno');
  });

  it('en_produccion_nunca_filtra_el_mensaje_interno', () => {
    vi.stubEnv('NODE_ENV', 'production');

    expect(mensajeDeError(new Error('detalle interno'), POR_DEFECTO)).toBe(POR_DEFECTO);
    expect(mensajeDeError('esto no es un Error', POR_DEFECTO)).toBe(POR_DEFECTO);
  });

  it('en_produccion_el_mensaje_del_backend_se_muestra_porque_es_generico', () => {
    vi.stubEnv('NODE_ENV', 'production');
    const fallo = new ApiError(500, 'Error interno', 'Fallo al cargar el deck');

    expect(mensajeDeError(fallo, POR_DEFECTO)).toBe('Error interno: Fallo al cargar el deck');
    expect(esSinSesion(new ApiError(401, 'No autorizado', 'Sesión caducada'))).toBe(true);
    expect(esSinSesion(fallo)).toBe(false);
  });
});
