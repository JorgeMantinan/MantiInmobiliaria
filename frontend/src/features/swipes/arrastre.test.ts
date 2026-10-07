import { describe, expect, it } from 'vitest';
import { debeDecidir, UMBRAL_ARRASTRE, VELOCIDAD_ARRASTRE } from '@/features/swipes/arrastre';

describe('debeDecidir', () => {
  it('decide_por_el_desplazamiento_cuando_supera_el_umbral', () => {
    expect(debeDecidir(UMBRAL_ARRASTRE + 10, 0)).toBe('LIKE');
    expect(debeDecidir(-(UMBRAL_ARRASTRE + 10), 0)).toBe('NOPE');
  });

  it('un_flick_rapido_corto_decide_aunque_no_llegue_al_umbral', () => {
    expect(debeDecidir(60, VELOCIDAD_ARRASTRE + 100)).toBe('LIKE');
    expect(debeDecidir(-60, -(VELOCIDAD_ARRASTRE + 100))).toBe('NOPE');
  });

  it('un_desplazamiento_pequeno_y_lento_no_decide', () => {
    expect(debeDecidir(60, 100)).toBeNull();
    expect(debeDecidir(-60, -100)).toBeNull();
    expect(debeDecidir(0, 0)).toBeNull();
  });
});
