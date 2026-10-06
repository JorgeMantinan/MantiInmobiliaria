import { describe, expect, it } from 'vitest';
import {
  hayErrores,
  validarRegistro,
} from '@/features/auth/validacion';

const VALIDO = {
  nombre: 'Ana Pérez',
  email: 'ana@example.com',
  password: 'secreto123',
  confirmacion: 'secreto123',
};

describe('validarRegistro', () => {
  it('datos_validos_no_produce_errores', () => {
    expect(hayErrores(validarRegistro(VALIDO))).toBe(false);
  });

  it('nombre_en_blanco_produce_error', () => {
    expect(validarRegistro({ ...VALIDO, nombre: '   ' }).nombre).toBe('Escribe tu nombre.');
  });

  it('email_sin_formato_produce_error', () => {
    expect(validarRegistro({ ...VALIDO, email: 'no-es-email' }).email).toBe(
      'Escribe un email válido.',
    );
  });

  it('password_de_7_caracteres_produce_error', () => {
    expect(validarRegistro({ ...VALIDO, password: '1234567', confirmacion: '1234567' }).password).toBe(
      'La contraseña debe tener al menos 8 caracteres.',
    );
  });

  it('password_de_73_caracteres_produce_error', () => {
    const larga = 'a'.repeat(73);
    expect(validarRegistro({ ...VALIDO, password: larga, confirmacion: larga }).password).toBe(
      'La contraseña no puede superar los 72 caracteres.',
    );
  });

  it('password_de_8_caracteres_es_valida', () => {
    expect(validarRegistro({ ...VALIDO, password: '12345678', confirmacion: '12345678' }).password).toBe(
      undefined,
    );
  });

  it('confirmacion_distinta_produce_error', () => {
    expect(validarRegistro({ ...VALIDO, confirmacion: 'otra1234' }).confirmacion).toBe(
      'Las contraseñas no coinciden.',
    );
  });

  it('hayErrores_detecta_algun_error', () => {
    expect(hayErrores({ email: 'Escribe un email válido.' })).toBe(true);
    expect(hayErrores({ email: undefined })).toBe(false);
    expect(hayErrores({})).toBe(false);
  });
});
