import { describe, expect, it } from 'vitest';
import { MENSAJES, mensajeDeError } from '@/features/backoffice/mensajes';
import { ApiError } from '@/lib/api';

describe('mensajeDeError del backoffice', () => {
  it('un_5xx_no_pinta_el_detail_cruso_sino_el_mensaje_fijo', () => {
    const volcado = new ApiError(500, 'Internal Server Error', 'NullPointerException:142');

    expect(mensajeDeError(volcado, MENSAJES.profesionales)).toBe(MENSAJES.profesionales);
    expect(mensajeDeError(new ApiError(503, 'Service Unavailable', 'Pool agotado'), MENSAJES.cambioEstado)).toBe(
      MENSAJES.cambioEstado,
    );
  });

  it('un_4xx_legible_se_pinta_con_su_detail_o_con_el_title_si_vacio', () => {
    expect(mensajeDeError(new ApiError(403, 'Prohibido', 'No tienes permisos'), MENSAJES.cambioEstado)).toBe(
      'No tienes permisos',
    );
    expect(mensajeDeError(new ApiError(409, 'Conflicto', ''), MENSAJES.cambioEstado)).toBe('Conflicto');
    expect(mensajeDeError(new ApiError(0, 'Error de conexión', 'No se ha podido contactar con el servidor.'), MENSAJES.profesionales)).toBe(
      'No se ha podido contactar con el servidor.',
    );
  });

  it('los_errores_que_no_son_de_la_api_conservan_su_mensaje_o_el_fallback', () => {
    expect(mensajeDeError(new Error('la API está caída'), MENSAJES.profesionales)).toBe('la API está caída');
    expect(mensajeDeError('algo inesperado', MENSAJES.profesionales)).toBe(MENSAJES.profesionales);
    expect(mensajeDeError(new Error(''), MENSAJES.cambioEstado)).toBe(MENSAJES.cambioEstado);
  });
});
