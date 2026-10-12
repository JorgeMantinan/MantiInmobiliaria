import { describe, expect, it } from 'vitest';
import {
  erroresDeCampoInmueble,
  hayErroresInmueble,
  validarInmueble,
} from '@/features/backoffice/validacion-inmueble';
import type { DatosInmuebleForm } from '@/features/backoffice/validacion-inmueble';

/** Payload mínimo VÁLIDO (espejo de los required del DTO). */
const VALIDO: DatosInmuebleForm = {
  titulo: 'Piso céntrico con ascensor',
  descripcion: 'Luminoso piso reformado en el centro histórico.',
  precio: '245000.50',
  superficieM2: '96',
  habitaciones: '3',
  banios: '1',
  tipo: 'PISO',
  estado: 'VENTA',
  direccion: 'Calle Mayor 1, 3º B',
  codigoPostal: '28013',
  municipio: 'Madrid',
  provincia: 'Madrid',
  lat: '40.4168',
  lng: '-3.7038',
  imagenUrl: 'https://picsum.photos/seed/inm1/800/600',
  destacado: false,
};

function con(campos: Partial<DatosInmuebleForm>): DatosInmuebleForm {
  return { ...VALIDO, ...campos };
}

describe('validarInmueble — espejo del backend', () => {
  it('un_payload_valido_no_devuelve_ningun_error', () => {
    expect(hayErroresInmueble(validarInmueble(VALIDO))).toBe(false);
  });

  it('el_titulo_vacio_o_demasiado_largo_es_error', () => {
    expect(validarInmueble(con({ titulo: '   ' })).titulo).toBe('Escribe el título del anuncio.');
    expect(validarInmueble(con({ titulo: 'x'.repeat(201) })).titulo).toMatch(/200/);
  });

  it('el_precio_requerido_invalido_o_con_mas_de_2_decimales_es_error', () => {
    expect(validarInmueble(con({ precio: '' })).precio).toBe('Escribe el precio.');
    expect(validarInmueble(con({ precio: '0' })).precio).toMatch(/0,01/);
    expect(validarInmueble(con({ precio: '-5' })).precio).toMatch(/número|0,01/);
    expect(validarInmueble(con({ precio: '10.123' })).precio).toMatch(/2 decimales/);
  });

  it('el_codigo_postal_admite_vacio_o_5_digitos_y_rechaza_el_resto', () => {
    expect(validarInmueble(con({ codigoPostal: '' })).codigoPostal).toBeUndefined();
    expect(validarInmueble(con({ codigoPostal: '28013' })).codigoPostal).toBeUndefined();
    expect(validarInmueble(con({ codigoPostal: '2801' })).codigoPostal).toMatch(/5 dígitos/);
    expect(validarInmueble(con({ codigoPostal: '2801A' })).codigoPostal).toMatch(/5 dígitos/);
  });

  it('las_coordenadas_deben_estar_en_el_rango_de_espana', () => {
    expect(validarInmueble(con({ lat: '26.9' })).lat).toMatch(/entre 27/);
    expect(validarInmueble(con({ lat: '44.1' })).lat).toMatch(/entre 27/);
    expect(validarInmueble(con({ lng: '-19.5' })).lng).toMatch(/entre -19/);
    expect(validarInmueble(con({ lng: '5.1' })).lng).toMatch(/entre -19/);
    expect(validarInmueble(con({ lat: 'abc' })).lat).toMatch(/número/);
  });

  it('la_imagen_solo_admite_url_https', () => {
    expect(validarInmueble(con({ imagenUrl: '' })).imagenUrl).toBeUndefined();
    expect(validarInmueble(con({ imagenUrl: 'http://ejemplo.com/a.jpg' })).imagenUrl).toMatch(
      /https/,
    );
    expect(validarInmueble(con({ imagenUrl: 'javascript:alert(1)' })).imagenUrl).toMatch(/https/);
  });

  it('las_cantidades_opcionales_vacias_son_validas_y_las_fuera_de_rango_no', () => {
    expect(validarInmueble(con({ habitaciones: '', banios: '', superficieM2: '' }))).toEqual({
      ...validarInmueble(VALIDO),
      habitaciones: undefined,
      banios: undefined,
      superficieM2: undefined,
    });
    expect(validarInmueble(con({ habitaciones: '201' })).habitaciones).toMatch(/200/);
    expect(validarInmueble(con({ banios: '51' })).banios).toMatch(/50/);
    expect(validarInmueble(con({ superficieM2: '0' })).superficieM2).toMatch(/mínimo 1/);
  });

  it('los_requeridos_de_texto_y_los_enums_no_pueden_ir_vacios', () => {
    const errores = validarInmueble(
      con({ direccion: '', municipio: '', provincia: '', tipo: '', estado: '' }),
    );
    expect(errores.direccion).toBe('Escribe la dirección.');
    expect(errores.municipio).toBe('Escribe el municipio.');
    expect(errores.provincia).toBe('Escribe la provincia.');
    expect(errores.tipo).toMatch(/tipo/);
    expect(errores.estado).toMatch(/estado/);
  });
});

describe('erroresDeCampoInmueble — mapeo del 400', () => {
  it('sólo_devuelve_claves_presentes_en_los_campos_del_form', () => {
    const mapeados = erroresDeCampoInmueble([
      { field: 'precio', message: 'debe ser mayor que 0' },
      { field: 'campoDesconocido', message: 'ignorado' },
      { message: 'sin field' },
    ]);
    expect(mapeados).toEqual({ precio: 'debe ser mayor que 0' });
  });
});
