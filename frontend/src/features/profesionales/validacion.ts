/** Validación client-side coherente con los DTOs/`CHECK` del backend (V4). */
import type { ErrorCampo } from '@/lib/tipos';

/** Describe el trabajo con al menos N caracteres (CHECK 10..2000 en BD). */
export const DESCRIPCION_MINIMA = 10;
export const DESCRIPCION_MAXIMA = 2000;
export const MUNICIPIO_MAXIMO = 120;
/** `varchar(120)` en `profesionales.nombre` y `solicitudes.nombre_contacto`. */
export const NOMBRE_MAXIMO = 120;
/** `varchar(255)` en `profesionales.email` y `solicitudes.email_contacto`. */
export const EMAIL_MAXIMO = 255;
export const GREMIOS_MINIMOS = 1;
export const GREMIOS_MAXIMOS = 8;

/** Campos con nombre estable de cada formulario (para mapear el 400 del backend). */
export const CAMPOS_SOLICITUD = [
  'nombreContacto',
  'emailContacto',
  'telefono',
  'municipio',
  'descripcion',
  'gremios',
] as const;

export const CAMPOS_ALTA = ['nombre', 'email', 'telefono', 'municipio', 'gremios'] as const;

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
    nombreContacto: validarNombre(datos.nombreContacto, 'Escribe tu nombre.'),
    emailContacto: validarEmail(datos.emailContacto),
    telefono: validarTelefono(datos.telefono),
    municipio: validarMunicipio(datos.municipio, 'Escribe el municipio.'),
    descripcion: validarDescripcion(datos.descripcion),
    gremios: validarGremios(datos.gremios),
  };
}

export function validarAlta(datos: DatosAlta): ErroresAlta {
  return {
    nombre: validarNombre(datos.nombre, 'Escribe tu nombre.'),
    email: validarEmail(datos.email),
    telefono: validarTelefono(datos.telefono),
    municipio: validarMunicipio(datos.municipio, undefined),
    gremios: validarGremios(datos.gremios),
  };
}

function validarNombre(nombre: string, mensajeVacio: string): string | undefined {
  const limpio = nombre.trim();
  if (limpio.length === 0) {
    return mensajeVacio;
  }
  return limpio.length <= NOMBRE_MAXIMO
    ? undefined
    : `El nombre no puede superar los ${NOMBRE_MAXIMO} caracteres.`;
}

function validarEmail(email: string): string | undefined {
  const limpio = email.trim();
  if (limpio.length > EMAIL_MAXIMO) {
    return `El email no puede superar los ${EMAIL_MAXIMO} caracteres.`;
  }
  return EMAIL_SENCILLO.test(limpio) ? undefined : 'Escribe un email válido.';
}

function validarDescripcion(descripcion: string): string | undefined {
  const limpio = descripcion.trim();
  if (limpio.length < DESCRIPCION_MINIMA) {
    return `Describe el trabajo con al menos ${DESCRIPCION_MINIMA} caracteres.`;
  }
  return limpio.length <= DESCRIPCION_MAXIMA
    ? undefined
    : `La descripción no puede superar los ${DESCRIPCION_MAXIMA} caracteres.`;
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

/**
 * Mapea los `errors[]` del ProblemDetail 400 a errores de campo (nombre de
 * campo ⇒ mensaje). Sólo devuelve claves presentes en `campos`: si ninguno
 * coincide, el llamador recurre al aviso global de fallback.
 */
export function erroresDeCampo(
  errores: readonly ErrorCampo[],
  campos: readonly string[],
): Readonly<Record<string, string>> {
  const resultado: Record<string, string> = {};
  for (const error of errores) {
    if (error.field !== undefined && campos.includes(error.field)) {
      resultado[error.field] = error.message;
    }
  }
  return resultado;
}
