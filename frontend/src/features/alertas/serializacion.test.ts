import { describe, expect, it } from 'vitest';
import { filtrosDesdeAlerta, filtrosParaAlerta, resumenFiltros } from './serializacion';

describe('filtrosParaAlerta', () => {
  it('convierte_los_textosANumero_y_descartaSort', () => {
    expect(
      filtrosParaAlerta({
        estado: 'VENTA',
        tipo: 'PISO',
        municipio: 'Madrid',
        precioMin: '100000',
        precioMax: '250000',
        habitacionesMin: '3',
        destacado: true,
        sort: 'precio_asc',
      }),
    ).toEqual({
      estado: 'VENTA',
      tipo: 'PISO',
      municipio: 'Madrid',
      precioMin: 100000,
      precioMax: 250000,
      habitacionesMin: 3,
      destacado: true,
    });
  });

  it('sin_filtros_devuelveObjetoVacio_yLosNoNumericosSeDescartan', () => {
    expect(filtrosParaAlerta({})).toEqual({});
    expect(filtrosParaAlerta({ precioMin: 'abc', habitacionesMin: '' })).toEqual({});
  });
});

describe('filtrosDesdeAlerta', () => {
  it('convierte_losNumerosATextoDeUrl', () => {
    expect(
      filtrosDesdeAlerta({
        estado: 'ALQUILER',
        precioMin: 500,
        habitacionesMin: 2,
        destacado: false,
      }),
    ).toEqual({
      estado: 'ALQUILER',
      precioMin: '500',
      habitacionesMin: '2',
      destacado: false,
    });
  });

  it('laSerializacion_esEstable_enIdaYVuelta', () => {
    const alerta = {
      estado: 'VENTA' as const,
      municipio: 'Madrid',
      precioMin: 100000,
      precioMax: 250000,
      habitacionesMin: 3,
    };
    expect(filtrosParaAlerta(filtrosDesdeAlerta(alerta))).toEqual(alerta);
  });
});

describe('resumenFiltros', () => {
  it('sinFiltros_devuelveTodosLosInmuebles', () => {
    expect(resumenFiltros({})).toBe('Todos los inmuebles');
  });

  it('componeLosFiltrosConEtiquetasLegibles', () => {
    const resumen = resumenFiltros({
      estado: 'VENTA',
      tipo: 'PISO',
      municipio: 'Madrid',
      precioMin: 100000,
      precioMax: 250000,
      habitacionesMin: 3,
      destacado: true,
    });

    expect(resumen).toContain('En venta');
    expect(resumen).toContain('Piso');
    expect(resumen).toContain('Madrid');
    expect(resumen).toContain('100.000');
    expect(resumen).toContain('250.000');
    expect(resumen).toContain('3+ habitaciones');
    expect(resumen).toContain('Sólo destacados');
  });

  it('conUnSoloLimiteDePrecio_usaDesdeOHasta', () => {
    expect(resumenFiltros({ precioMax: 200000 })).toContain('Hasta');
    expect(resumenFiltros({ precioMin: 200000 })).toContain('Desde');
  });
});
