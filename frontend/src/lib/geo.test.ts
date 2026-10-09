import { describe, expect, it } from 'vitest';
import { latLngATile, TAMANO_TESELA } from '@/lib/geo';

const LATS = [-80, -30, 0, 20, 40, 60, 85.05112878];
const LNGS = [-179.9, -3.7038, 0, 2.9, 179.9];
const ZOOMS = [0, 1, 3, 8, 16];

describe('latLngATile', () => {
  it('en_el_ecuador_y_el_meridiano_con_zoom_1_devuelve_la_tesela_1_1', () => {
    expect(latLngATile(0, 0, 1)).toEqual({ x: 1, y: 1, offsetX: 0, offsetY: 0 });
  });

  it('una_longitud_de_90_grados_a_zoom_1_cae_en_el_borde_derecho_de_la_tesela_1', () => {
    // (90 + 180) / 360 * 2 = 1,5 → tesela x=1 con 128 px de desplazamiento.
    expect(latLngATile(0, 90, 1)).toEqual({ x: 1, y: 1, offsetX: 128, offsetY: 0 });
  });

  it('a_latitud_mas_alta_le_corresponde_un_y_menor', () => {
    const sur = latLngATile(20, -3.7038, 8);
    const norte = latLngATile(60, -3.7038, 8);
    expect(norte.y).toBeLessThan(sur.y);
    expect(latLngATile(85, -3.7038, 8).y).toBeLessThan(latLngATile(0, -3.7038, 8).y);
  });

  it('mantiene_x_e_y_dentro_del_rango_0_a_2_z_menos_1_para_cualquier_zoom', () => {
    for (const zoom of ZOOMS) {
      const maximo = 2 ** zoom - 1;
      for (const lat of LATS) {
        for (const lng of LNGS) {
          const { x, y } = latLngATile(lat, lng, zoom);
          expect(x).toBeGreaterThanOrEqual(0);
          expect(x).toBeLessThanOrEqual(maximo);
          expect(y).toBeGreaterThanOrEqual(0);
          expect(y).toBeLessThanOrEqual(maximo);
          expect(Number.isInteger(x)).toBe(true);
          expect(Number.isInteger(y)).toBe(true);
        }
      }
    }
  });

  it('devuelve_los_offsets_dentro_del_intervalo_0_256', () => {
    for (const zoom of ZOOMS) {
      for (const lat of LATS) {
        for (const lng of LNGS) {
          const { offsetX, offsetY } = latLngATile(lat, lng, zoom);
          expect(offsetX).toBeGreaterThanOrEqual(0);
          expect(offsetX).toBeLessThan(TAMANO_TESELA);
          expect(offsetY).toBeGreaterThanOrEqual(0);
          expect(offsetY).toBeLessThan(TAMANO_TESELA);
        }
      }
    }
  });

  it('en_zoom_0_solo_hay_una_unica_tesela_para_toda_la_tierra', () => {
    const tesela = latLngATile(40.4168, -3.7038, 0);
    expect(tesela.x).toBe(0);
    expect(tesela.y).toBe(0);
    expect(tesela.offsetX).toBeGreaterThanOrEqual(0);
    expect(tesela.offsetY).toBeLessThan(TAMANO_TESELA);
  });

  it('recorta_latitudes_y_longitudes_fuera_de_rango_en_vez_de_fallar', () => {
    const polos = latLngATile(90, 200, 4);
    const limite = latLngATile(85.05112878, 180, 4);
    expect(polos.y).toBe(limite.y);
    expect(polos.x).toBe(15);
    expect(polos.offsetX).toBeLessThan(TAMANO_TESELA);
  });
});
