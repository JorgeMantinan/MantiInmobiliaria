import { describe, expect, it } from 'vitest';
import {
  construirQueryInmuebles,
  HABITACIONES_MAX,
  leerFiltros,
  PRECIO_MAXIMO_CONSULTA,
  rangoPrecioInconsistente,
} from '@/features/inmuebles/filtros';

const params = (consulta: string) => new URLSearchParams(consulta);

describe('leerFiltros', () => {
  it('ignora_valores_de_enumerados_que_el_backend_no_admite', () => {
    expect(leerFiltros(params('tipo=BASURA&estado=XYZ&sort=nada')).tipo).toBeUndefined();
    expect(leerFiltros(params('tipo=BASURA&estado=XYZ&sort=nada')).estado).toBeUndefined();
    expect(leerFiltros(params('tipo=BASURA&estado=XYZ&sort=nada')).sort).toBeUndefined();
  });

  it('ignora_numeros_que_no_sean_enteros_validos', () => {
    const filtros = leerFiltros(params('precioMin=abc&precioMax=-100&habitacionesMin=1.5'));
    expect(filtros.precioMin).toBeUndefined();
    expect(filtros.precioMax).toBeUndefined();
    expect(filtros.habitacionesMin).toBeUndefined();
  });

  it('descarta_el_par_de_precio_cuando_el_rango_va_invertido', () => {
    // El backend responde 400 si precioMin > precioMax: no se manda ninguno.
    const filtros = leerFiltros(params('precioMin=300000&precioMax=100000'));
    expect(filtros.precioMin).toBeUndefined();
    expect(filtros.precioMax).toBeUndefined();
  });

  it('conserva_el_rango_de_precio_cuando_es_coherente', () => {
    const filtros = leerFiltros(params('precioMin=100000&precioMax=300000'));
    expect(filtros.precioMin).toBe('100000');
    expect(filtros.precioMax).toBe('300000');
  });

  it('recorta_el_municipio_a_120_caracteres', () => {
    const filtros = leerFiltros(params(`municipio=${'a'.repeat(200)}`));
    expect(filtros.municipio).toHaveLength(120);
  });

  it('acota_las_habitaciones_a_lo_que_el_backend_puede_convertir_a_short', () => {
    expect(leerFiltros(params('habitacionesMin=500')).habitacionesMin).toBeUndefined();
    expect(leerFiltros(params(`habitacionesMin=${HABITACIONES_MAX}`)).habitacionesMin).toBe('100');
    expect(leerFiltros(params(`precioMin=${PRECIO_MAXIMO_CONSULTA + 1}`)).precioMin).toBeUndefined();
  });

  it('solo_acepta_destacado_como_booleano_real', () => {
    expect(leerFiltros(params('destacado=quizas')).destacado).toBeUndefined();
    expect(leerFiltros(params('destacado=true')).destacado).toBe(true);
    expect(leerFiltros(params('destacado=false')).destacado).toBe(false);
  });
});

describe('construirQueryInmuebles', () => {
  it('nunca_serializa_un_rango_de_precio_invertido', () => {
    const query = construirQueryInmuebles({ precioMin: '300000', precioMax: '100000' });
    expect(query).not.toContain('precioMin');
    expect(query).not.toContain('precioMax');
  });

  it('serializa_el_resto_de_filtros_con_limit_y_cursor', () => {
    const query = construirQueryInmuebles(
      { estado: 'VENTA', destacado: true, sort: 'precio_asc' },
      { cursor: 'abc', limit: 20 },
    );
    expect(query).toBe('?estado=VENTA&destacado=true&sort=precio_asc&cursor=abc&limit=20');
  });
});

describe('rangoPrecioInconsistente', () => {
  it('detecta_minimo_superior_al_maximo_y_acepta_el_resto', () => {
    expect(rangoPrecioInconsistente(params('precioMin=300&precioMax=100'))).toBe(true);
    expect(rangoPrecioInconsistente(params('precioMin=100&precioMax=300'))).toBe(false);
    expect(rangoPrecioInconsistente(params('precioMin=100&precioMax=100'))).toBe(false);
    expect(rangoPrecioInconsistente(params('precioMin=100'))).toBe(false);
    expect(rangoPrecioInconsistente(params('precioMin=abc&precioMax=100'))).toBe(false);
  });
});
