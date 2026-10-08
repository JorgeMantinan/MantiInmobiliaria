import { describe, expect, it } from 'vitest';
import {
  DESCRIPCION_MAXIMA,
  EMAIL_MAXIMO,
  NOMBRE_MAXIMO,
  erroresDeCampo,
  validarAlta,
  validarSolicitud,
} from '@/features/profesionales/validacion';

const SOLICITUD: Parameters<typeof validarSolicitud>[0] = {
  nombreContacto: 'Ana Pérez',
  emailContacto: 'ana@ejemplo.com',
  telefono: '',
  municipio: 'Oviedo',
  descripcion: 'Se ha roto el grifo del baño',
  gremios: ['fontanero'],
};

const ALTA: Parameters<typeof validarAlta>[0] = {
  nombre: 'Luis Fontanero',
  email: 'luis@ejemplo.com',
  telefono: '',
  municipio: '',
  gremios: ['fontanero'],
};

/** Email válido de N caracteres (12 de '@ejemplo.com'). */
function emailDe(longitud: number): string {
  return `${'a'.repeat(longitud - 12)}@ejemplo.com`;
}

describe('límites de validación', () => {
  it('validarSolicitud_con_nombre_de_mas_de_120_caracteres_devuelve_error', () => {
    expect(
      validarSolicitud({ ...SOLICITUD, nombreContacto: 'A'.repeat(NOMBRE_MAXIMO + 1) })
        .nombreContacto,
    ).toBe('El nombre no puede superar los 120 caracteres.');
    expect(
      validarSolicitud({ ...SOLICITUD, nombreContacto: 'A'.repeat(NOMBRE_MAXIMO) })
        .nombreContacto,
    ).toBeUndefined();
  });

  it('validarSolicitud_con_email_de_mas_de_255_caracteres_devuelve_error', () => {
    expect(
      validarSolicitud({ ...SOLICITUD, emailContacto: emailDe(EMAIL_MAXIMO + 1) }).emailContacto,
    ).toBe('El email no puede superar los 255 caracteres.');
    expect(
      validarSolicitud({ ...SOLICITUD, emailContacto: emailDe(EMAIL_MAXIMO) }).emailContacto,
    ).toBeUndefined();
  });

  it('validarSolicitud_con_descripcion_de_mas_de_2000_caracteres_devuelve_error', () => {
    expect(
      validarSolicitud({ ...SOLICITUD, descripcion: 'B'.repeat(DESCRIPCION_MAXIMA + 1) })
        .descripcion,
    ).toBe('La descripción no puede superar los 2000 caracteres.');
    expect(
      validarSolicitud({ ...SOLICITUD, descripcion: 'B'.repeat(DESCRIPCION_MAXIMA) }).descripcion,
    ).toBeUndefined();
  });

  it('validarAlta_aplica_los_mismos_limites_de_nombre_y_email', () => {
    expect(validarAlta({ ...ALTA, nombre: 'A'.repeat(NOMBRE_MAXIMO + 1) }).nombre).toBe(
      'El nombre no puede superar los 120 caracteres.',
    );
    expect(validarAlta({ ...ALTA, nombre: 'A'.repeat(NOMBRE_MAXIMO) }).nombre).toBeUndefined();
    expect(validarAlta({ ...ALTA, email: emailDe(EMAIL_MAXIMO + 1) }).email).toBe(
      'El email no puede superar los 255 caracteres.',
    );
  });

  it('errores_de_campo_solo_mapea_los_field_conocidos_del_problem_detail', () => {
    const resultado = erroresDeCampo(
      [
        { field: 'descripcion', message: 'demasiado corta' },
        { field: 'campoInexistente', message: 'se ignora' },
        { message: 'sin campo asociado' },
      ],
      ['nombreContacto', 'descripcion', 'gremios'],
    );

    expect(resultado).toEqual({ descripcion: 'demasiado corta' });
  });
});
