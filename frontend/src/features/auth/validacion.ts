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

export function validarRegistro(datos: DatosRegistroConConfirmacion): ErroresRegistro {
  return {
    nombre: datos.nombre.trim().length === 0 ? 'Escribe tu nombre.' : undefined,
    email: EMAIL_SENCILLO.test(datos.email.trim()) ? undefined : 'Escribe un email válido.',
    password: validarPassword(datos.password),
    confirmacion:
      datos.confirmacion === datos.password ? undefined : 'Las contraseñas no coinciden.',
  };
}

function validarPassword(password: string): string | undefined {
  if (password.length < PASSWORD_MINIMO) {
    return `La contraseña debe tener al menos ${PASSWORD_MINIMO} caracteres.`;
  }
  if (password.length > PASSWORD_MAXIMO) {
    return `La contraseña no puede superar los ${PASSWORD_MAXIMO} caracteres.`;
  }
  return undefined;
}

export function hayErrores(errores: ErroresRegistro): boolean {
  return Object.values(errores).some((mensaje) => mensaje !== undefined);
}
