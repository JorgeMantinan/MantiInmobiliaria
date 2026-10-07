import { describe, expect, it } from 'vitest';
import {
  IMAGEN_ALTO_PCT,
  IMAGEN_ANCHO_PCT,
  ubicar,
} from '@/features/inmuebles/components/mapa-tarjeta';
import { TAMANO_TESELA } from '@/lib/geo';

describe('ubicar', () => {
  it('con_offset_en_el_borde_izquierdo_deja_el_pin_en_el_cero_de_la_caja', () => {
    const { inicio, punto } = ubicar(0, IMAGEN_ANCHO_PCT);
    expect(inicio).toBe(0);
    expect(punto).toBe(0);
  });

  it('con_offset_en_el_centro_centra_el_pin_en_la_caja', () => {
    const horizontal = ubicar(TAMANO_TESELA / 2, IMAGEN_ANCHO_PCT);
    expect(horizontal.punto).toBeCloseTo(50, 5);

    const vertical = ubicar(TAMANO_TESELA / 2, IMAGEN_ALTO_PCT);
    expect(vertical.punto).toBeCloseTo(50, 5);
  });

  it('con_offset_en_el_borde_derecho_lleva_el_pin_al_cien_por_ciento', () => {
    const { inicio, punto } = ubicar(TAMANO_TESELA - 0.001, IMAGEN_ANCHO_PCT);
    expect(punto).toBeCloseTo(100, 2);
    expect(inicio).toBeLessThanOrEqual(0);
  });

  it('con_offset_en_el_borde_inferior_tambien_llega_al_fondo_de_la_caja', () => {
    const { inicio, punto } = ubicar(TAMANO_TESELA - 0.001, IMAGEN_ALTO_PCT);
    expect(punto).toBeCloseTo(100, 2);
    expect(inicio).toBeLessThanOrEqual(0);
  });

  it('el_tile_cubre_la_caja_entera_y_el_pin_no_sale_nunca', () => {
    for (const tamano of [IMAGEN_ANCHO_PCT, IMAGEN_ALTO_PCT]) {
      for (let offset = 0; offset <= TAMANO_TESELA; offset += 7) {
        const { inicio, punto } = ubicar(offset, tamano);
        // El tile siempre tapa la caja (0..100).
        expect(inicio).toBeLessThanOrEqual(0);
        expect(inicio + tamano).toBeGreaterThanOrEqual(100);
        // Y el pin queda dentro de la caja.
        expect(punto).toBeGreaterThanOrEqual(0);
        expect(punto).toBeLessThanOrEqual(100);
      }
    }
  });
});
