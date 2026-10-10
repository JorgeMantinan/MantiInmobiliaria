/** Validación client-side coherente con `RegistroRequest` del backend. */

export const PASSWORD_MINIMO = 8;
export const PASSWORD_MAXIMO = 72;

const EMAIL_SENCILLO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export type ErroresRegistro = Readonly<{
  nombre?: string;
  email?: string;
  password?: string;
  confirmacion?: string;
}>;

export type DatosRegistroConConfirmacion = Readonly<{
  nombre: string;
  email: string;
  password: string;
  confirmacion: string;
}>;

/** ¿El email tiene formato aceptable? (compartido por registro y recuperación). */
export function validarEmail(email: string): string | undefined {
  return EMAIL_SENCILLO.test(email.trim()) ? undefined : 'Escribe un email válido.';
}

/** Reglas 8-72 de la contraseña (idénticas a las del registro). */
export function validarPassword(password: string): string | undefined {
  if (password.length < PASSWORD_MINIMO) {
    return `La contraseña debe tener al menos ${PASSWORD_MINIMO} caracteres.`;
  }
  if (password.length > PASSWORD_MAXIMO) {
    return `La contraseña no puede superar los ${PASSWORD_MAXIMO} caracteres.`;
  }
  return undefined;
}

export type ErroresRestablecer = Readonly<{
  password?: string;
  confirmacion?: string;
}>;

/** Validación del formulario de restablecimiento: contraseña nueva + confirmación. */
export function validarRestablecer(password: string, confirmacion: string): ErroresRestablecer {
  return {
    password: validarPassword(password),
    confirmacion:
      confirmacion === password ? undefined : 'Las contraseñas no coinciden.',
  };
}

export function validarRegistro(datos: DatosRegistroConConfirmacion): ErroresRegistro {
  return {
    nombre: datos.nombre.trim().length === 0 ? 'Escribe tu nombre.' : undefined,
    email: validarEmail(datos.email),
    password: validarPassword(datos.password),
    confirmacion:
      datos.confirmacion === datos.password ? undefined : 'Las contraseñas no coinciden.',
  };
}

export function hayErrores(errores: ErroresRegistro | ErroresRestablecer): boolean {
  return Object.values(errores).some((mensaje) => mensaje !== undefined);
}
