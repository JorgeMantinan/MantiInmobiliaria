/** Validación client-side coherente con los DTOs del backend de profesionales. */

/** Describe el trabajo con al menos N caracteres. */
export const DESCRIPCION_MINIMA = 10;
export const MUNICIPIO_MAXIMO = 120;
export const GREMIOS_MINIMOS = 1;
export const GREMIOS_MAXIMOS = 8;

const EMAIL_SENCILLO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
/** Teléfono opcional: dígito o `+` inicial y entre 7 y 25 caracteres en total. */
const TELEFONO_SENCILLO = /^[+\d][\d\s().-]{6,24}$/;

export type DatosSolicitud = Readonly<{
  nombreContacto: string;
  emailContacto: string;
  telefono: string;
  municipio: string;
  descripcion: string;
  gremios: readonly string[];
}>;

export type ErroresSolicitud = Readonly<{
  nombreContacto?: string;
  emailContacto?: string;
  telefono?: string;
  municipio?: string;
  descripcion?: string;
  gremios?: string;
}>;

export type DatosAlta = Readonly<{
  nombre: string;
  email: string;
  telefono: string;
  municipio: string;
  gremios: readonly string[];
}>;

export type ErroresAlta = Readonly<{
  nombre?: string;
  email?: string;
  telefono?: string;
  municipio?: string;
  gremios?: string;
}>;

export function validarSolicitud(datos: DatosSolicitud): ErroresSolicitud {
  return {
    nombreContacto:
      datos.nombreContacto.trim().length === 0 ? 'Escribe tu nombre.' : undefined,
    emailContacto: validarEmail(datos.emailContacto),
    telefono: validarTelefono(datos.telefono),
    municipio: validarMunicipio(datos.municipio, 'Escribe el municipio.'),
    descripcion:
      datos.descripcion.trim().length < DESCRIPCION_MINIMA
        ? `Describe el trabajo con al menos ${DESCRIPCION_MINIMA} caracteres.`
        : undefined,
    gremios: validarGremios(datos.gremios),
  };
}

export function validarAlta(datos: DatosAlta): ErroresAlta {
  return {
    nombre: datos.nombre.trim().length === 0 ? 'Escribe tu nombre.' : undefined,
    email: validarEmail(datos.email),
    telefono: validarTelefono(datos.telefono),
    municipio: validarMunicipio(datos.municipio, undefined),
    gremios: validarGremios(datos.gremios),
  };
}

function validarEmail(email: string): string | undefined {
  return EMAIL_SENCILLO.test(email.trim()) ? undefined : 'Escribe un email válido.';
}

function validarTelefono(telefono: string): string | undefined {
  const limpio = telefono.trim();
  if (limpio.length === 0) {
    return undefined;
  }
  return TELEFONO_SENCILLO.test(limpio) ? undefined : 'Escribe un teléfono válido.';
}

function validarMunicipio(municipio: string, mensajeVacio: string | undefined): string | undefined {
  const limpio = municipio.trim();
  if (limpio.length === 0) {
    return mensajeVacio;
  }
  return limpio.length <= MUNICIPIO_MAXIMO
    ? undefined
    : `El municipio no puede superar los ${MUNICIPIO_MAXIMO} caracteres.`;
}

function validarGremios(gremios: readonly string[]): string | undefined {
  if (gremios.length < GREMIOS_MINIMOS) {
    return 'Selecciona al menos un gremio.';
  }
  if (gremios.length > GREMIOS_MAXIMOS) {
    return `Puedes elegir como máximo ${GREMIOS_MAXIMOS} gremios.`;
  }
  return undefined;
}

/** true si algún campo tiene mensaje de error. */
export function hayErrores(errores: Readonly<Record<string, string | undefined>>): boolean {
  return Object.values(errores).some((mensaje) => mensaje !== undefined);
}
