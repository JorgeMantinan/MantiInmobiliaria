/**
 * Validación client-side del formulario de inmuebles, ESPEJO de las
 * constraints de `InmuebleRequest` (jakarta validation) y del CHECK de la
 * entidad: mismos límites, mismos patrones y mensajes en español.
 *
 * El formulario trabaja con Strings crudos (como llegan del DOM); la
 * conversión al payload (`InmuebleEnvio`) la hace `construirEnvio` sólo
 * cuando la validación ya ha pasado.
 */
import type { ErrorCampo } from '@/lib/tipos';
import type { EstadoInmueble, TipoInmueble } from '@/features/inmuebles/types';
import type { InmuebleEnvio } from './types';

/** `@Size(max = 200)` del DTO. */
export const TITULO_MAXIMO = 200;
/** `@Size(max = 5000)` del DTO. */
export const DESCRIPCION_MAXIMA = 5000;
/** `@Size(max = 500)` de `direccion`. */
export const DIRECCION_MAXIMA = 500;
/** `@Size(max = 120)` de `municipio` y `provincia`. */
export const LOCALIDAD_MAXIMA = 120;
/** `@Size(max = 500)` de `imagenUrl`. */
export const IMAGEN_MAXIMA = 500;
/** `@Digits(integer = 10, fraction = 2)` del precio. */
export const PRECIO_MAXIMO = 9_999_999_999.99;
/** `@Max(200)` de `habitaciones`. */
export const HABITACIONES_MAXIMO = 200;
/** `@Max(50)` de `banios`. */
export const BANIOS_MAXIMO = 50;
/** `Integer.MAX_VALUE`: techo del `superficie_m2` (integer) en BD. */
export const SUPERFICIE_MAXIMA = 2_147_483_647;
/** Coordenadas de España (`@DecimalMin/Max` de lat y lng). */
export const LAT_MINIMA = 27.0;
export const LAT_MAXIMA = 44.0;
export const LNG_MINIMA = -19.0;
export const LNG_MAXIMA = 5.0;

export const TIPOS_INMUEBLE: readonly TipoInmueble[] = [
  'PISO',
  'CASA',
  'ATICO',
  'DUPLEX',
  'ESTUDIO',
  'LOCAL',
  'TERRENO',
];

export const ESTADOS_INMUEBLE: readonly EstadoInmueble[] = ['VENTA', 'ALQUILER'];

/** Campos del form/DTO, para mapear el 400 (`errors[]`) a errores de campo. */
export const CAMPOS_INMUEBLE = [
  'titulo',
  'descripcion',
  'precio',
  'superficieM2',
  'habitaciones',
  'banios',
  'tipo',
  'estado',
  'direccion',
  'codigoPostal',
  'municipio',
  'provincia',
  'lat',
  'lng',
  'imagenUrl',
] as const;

/** Crudo del formulario: todo String (texto del input) más el checkbox. */
export type DatosInmuebleForm = Readonly<{
  titulo: string;
  descripcion: string;
  precio: string;
  superficieM2: string;
  habitaciones: string;
  banios: string;
  tipo: string;
  estado: string;
  direccion: string;
  codigoPostal: string;
  municipio: string;
  provincia: string;
  lat: string;
  lng: string;
  imagenUrl: string;
  destacado: boolean;
}>;

export type ErroresInmueble = Readonly<{
  titulo?: string;
  descripcion?: string;
  precio?: string;
  superficieM2?: string;
  habitaciones?: string;
  banios?: string;
  tipo?: string;
  estado?: string;
  direccion?: string;
  codigoPostal?: string;
  municipio?: string;
  provincia?: string;
  lat?: string;
  lng?: string;
  imagenUrl?: string;
}>;

const CODIGO_POSTAL_ES = /^[0-9]{5}$/;
/** Sólo URL https con caracteres de URL RFC 3986 (idéntico al pattern del DTO). */
const IMAGEN_HTTPS = /^https:\/\/[A-Za-z0-9\-._~:/?#[\]@!$&'()*+,;=%]+$/;
/** Hasta 2 decimales, con o sin parte entera (BigDecimal acepta `0.5`). */
const DECIMALES_OK = /^\d+(\.\d{1,2})?$/;

export function validarInmueble(datos: DatosInmuebleForm): ErroresInmueble {
  return {
    titulo: validarTitulo(datos.titulo),
    descripcion: validarDescripcion(datos.descripcion),
    precio: validarPrecio(datos.precio),
    superficieM2: validarEnteroOpcional(
      datos.superficieM2,
      'la superficie',
      1,
      SUPERFICIE_MAXIMA,
    ),
    habitaciones: validarEnteroOpcional(
      datos.habitaciones,
      'las habitaciones',
      0,
      HABITACIONES_MAXIMO,
    ),
    banios: validarEnteroOpcional(datos.banios, 'los baños', 0, BANIOS_MAXIMO),
    tipo: validarEnum(datos.tipo, TIPOS_INMUEBLE, 'Selecciona el tipo de inmueble.'),
    estado: validarEnum(datos.estado, ESTADOS_INMUEBLE, 'Selecciona el estado (venta o alquiler).'),
    direccion: validarRequerido(datos.direccion, DIRECCION_MAXIMA, 'Escribe la dirección.'),
    codigoPostal: validarCodigoPostal(datos.codigoPostal),
    municipio: validarRequerido(datos.municipio, LOCALIDAD_MAXIMA, 'Escribe el municipio.'),
    provincia: validarRequerido(datos.provincia, LOCALIDAD_MAXIMA, 'Escribe la provincia.'),
    lat: validarCoordenada(datos.lat, LAT_MINIMA, LAT_MAXIMA, 'La latitud'),
    lng: validarCoordenada(datos.lng, LNG_MINIMA, LNG_MAXIMA, 'La longitud'),
    imagenUrl: validarImagenUrl(datos.imagenUrl),
  };
}

function validarTitulo(titulo: string): string | undefined {
  const limpio = titulo.trim();
  if (limpio.length === 0) return 'Escribe el título del anuncio.';
  return limpio.length <= TITULO_MAXIMO
    ? undefined
    : `El título no puede superar los ${TITULO_MAXIMO} caracteres.`;
}

function validarDescripcion(descripcion: string): string | undefined {
  const limpio = descripcion.trim();
  return limpio.length <= DESCRIPCION_MAXIMA
    ? undefined
    : `La descripción no puede superar los ${DESCRIPCION_MAXIMA} caracteres.`;
}

function validarPrecio(precio: string): string | undefined {
  const limpio = precio.trim().replace(',', '.');
  if (limpio.length === 0) return 'Escribe el precio.';
  if (!DECIMALES_OK.test(limpio)) {
    return 'El precio debe ser un número con hasta 2 decimales.';
  }
  return Number(limpio) >= 0.01 && Number(limpio) <= PRECIO_MAXIMO
    ? undefined
    : 'El precio debe estar entre 0,01 € y 9.999.999.999,99 €.';
}

/**
 * Entero opcional con redacción neutral (sin concordancia plural/singular
 * forzada): `nombre` lleva el artículo, p. ej. `'la superficie'`.
 */
function validarEnteroOpcional(
  valor: string,
  nombre: string,
  minimo: number,
  maximo: number | undefined,
): string | undefined {
  const limpio = valor.trim();
  if (limpio.length === 0) return undefined;
  if (!/^\d+$/.test(limpio)) return `Introduce un número entero para ${nombre}.`;
  const numero = Number(limpio);
  if (numero < minimo) return `Introduce un valor igual o superior a ${minimo} para ${nombre}.`;
  if (maximo !== undefined && numero > maximo) {
    return `Introduce un valor igual o inferior a ${maximo} para ${nombre}.`;
  }
  return undefined;
}

function validarEnum<T extends string>(
  valor: string,
  permitidos: readonly T[],
  mensaje: string,
): string | undefined {
  return permitidos.includes(valor as T) ? undefined : mensaje;
}

function validarRequerido(valor: string, maximo: number, mensajeVacio: string): string | undefined {
  const limpio = valor.trim();
  if (limpio.length === 0) return mensajeVacio;
  return limpio.length <= maximo ? undefined : `No puede superar los ${maximo} caracteres.`;
}

function validarCodigoPostal(codigoPostal: string): string | undefined {
  const limpio = codigoPostal.trim();
  if (limpio.length === 0) return undefined;
  return CODIGO_POSTAL_ES.test(limpio)
    ? undefined
    : 'El código postal debe tener exactamente 5 dígitos.';
}

function validarCoordenada(
  valor: string,
  minimo: number,
  maximo: number,
  etiqueta: string,
): string | undefined {
  const limpio = valor.trim().replace(',', '.');
  if (limpio.length === 0) return `Escribe ${etiqueta.toLowerCase()}.`;
  const numero = Number(limpio);
  if (!Number.isFinite(numero)) return `${etiqueta} debe ser un número.`;
  return numero >= minimo && numero <= maximo
    ? undefined
    : `${etiqueta} debe estar entre ${minimo} y ${maximo} (España).`;
}

function validarImagenUrl(imagenUrl: string): string | undefined {
  const limpio = imagenUrl.trim();
  if (limpio.length === 0) return undefined;
  if (limpio.length > IMAGEN_MAXIMA) {
    return `La imagen no puede superar los ${IMAGEN_MAXIMA} caracteres.`;
  }
  return IMAGEN_HTTPS.test(limpio) ? undefined : 'La imagen debe ser una URL https válida.';
}

/** true si algún campo tiene mensaje de error. */
export function hayErroresInmueble(errores: ErroresInmueble): boolean {
  return Object.values(errores).some((mensaje) => mensaje !== undefined);
}

/**
 * Payload del DTO: trims, opcionales vacíos → `null` y números ya convertidos.
 * Sólo debe llamarse tras una validación sin errores.
 */
export function construirEnvio(datos: DatosInmuebleForm): InmuebleEnvio {
  return {
    titulo: datos.titulo.trim(),
    descripcion: vacioANull(datos.descripcion),
    precio: numero(datos.precio),
    superficieM2: enteroOpcional(datos.superficieM2),
    habitaciones: enteroOpcional(datos.habitaciones),
    banios: enteroOpcional(datos.banios),
    tipo: datos.tipo as TipoInmueble,
    estado: datos.estado as EstadoInmueble,
    direccion: datos.direccion.trim(),
    codigoPostal: vacioANull(datos.codigoPostal),
    municipio: datos.municipio.trim(),
    provincia: datos.provincia.trim(),
    lat: numero(datos.lat),
    lng: numero(datos.lng),
    imagenUrl: vacioANull(datos.imagenUrl),
    destacado: datos.destacado,
  };
}

function vacioANull(valor: string): string | null {
  const limpio = valor.trim();
  return limpio.length === 0 ? null : limpio;
}

function numero(valor: string): number {
  return Number(valor.trim().replace(',', '.'));
}

function enteroOpcional(valor: string): number | null {
  const limpio = valor.trim();
  return limpio.length === 0 ? null : Number(limpio);
}

/**
 * Mapea los `errors[]` del ProblemDetail 400 a errores de campo (nombre de
 * campo ⇒ mensaje), igual que el patrón de profesionales: sólo claves
 * presentes en `campos`; el resto lo gestiona el aviso global del form.
 */
export function erroresDeCampoInmueble(
  errores: readonly ErrorCampo[],
  campos: readonly string[] = CAMPOS_INMUEBLE,
): Readonly<Record<string, string>> {
  const resultado: Record<string, string> = {};
  for (const error of errores) {
    if (error.field !== undefined && campos.includes(error.field)) {
      resultado[error.field] = error.message;
    }
  }
  return resultado;
}
