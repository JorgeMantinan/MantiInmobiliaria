import { Campo } from '@/components/ui/campo';
import { CampoTextoArea } from '@/components/ui/campo-textarea';
import type { InmuebleDetalle } from '@/features/inmuebles/types';
import {
  DESCRIPCION_MAXIMA,
  DIRECCION_MAXIMA,
  ESTADOS_INMUEBLE,
  IMAGEN_MAXIMA,
  LOCALIDAD_MAXIMA,
  TITULO_MAXIMO,
  TIPOS_INMUEBLE,
} from '../validacion-inmueble';
import type { DatosInmuebleForm, ErroresInmueble } from '../validacion-inmueble';

/** Errores + valores iniciales (edición) de los grupos de campos. */
type CamposProps = Readonly<{
  errores: ErroresInmueble;
  /** `undefined` ⇒ alta (vacío); en edición, los strings del inmueble. */
  valores?: ValoresCampos;
}>;

/** Precarga del form en edición: todo String ya convertido. */
export type ValoresCampos = Readonly<{
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

/** Vacío por defecto (alta). */
const VACIOS: ValoresCampos = {
  titulo: '',
  descripcion: '',
  precio: '',
  superficieM2: '',
  habitaciones: '',
  banios: '',
  tipo: '',
  estado: '',
  direccion: '',
  codigoPostal: '',
  municipio: '',
  provincia: '',
  lat: '',
  lng: '',
  imagenUrl: '',
  destacado: false,
};

const CLASE_CAMPO =
  'rounded-md border border-borde bg-superficie px-3 py-2 text-texto outline-none transition-colors focus:ring-2 focus:ring-acento-nogal';

/** Etiqueta legible de tipo/estado (`PISO` → `Piso`). */
function etiqueta(valor: string): string {
  return valor.charAt(0) + valor.slice(1).toLowerCase();
}

/** Anuncio: título, descripción, precio, tipo, estado, destacado e imagen. */
export function CamposAnuncio({ errores, valores = VACIOS }: CamposProps) {
  return (
    <>
      <Campo
        id="inm-titulo"
        name="titulo"
        etiqueta="Título del anuncio"
        type="text"
        maxLength={TITULO_MAXIMO}
        defaultValue={valores.titulo}
        error={errores.titulo}
        required
      />
      <CampoTextoArea
        id="inm-descripcion"
        name="descripcion"
        etiqueta="Descripción (opcional)"
        rows={4}
        maxLength={DESCRIPCION_MAXIMA}
        defaultValue={valores.descripcion}
        error={errores.descripcion}
      />
      <Campo
        id="inm-precio"
        name="precio"
        etiqueta="Precio en euros"
        // text + inputMode decimal: admite la coma española (`320000,50`) que
        // `type=number` vaciaría en silencio; la normalización coma→punto la
        // hace la validación (`validarPrecio`/`construirEnvio`).
        type="text"
        inputMode="decimal"
        placeholder="245000,50"
        defaultValue={valores.precio}
        error={errores.precio}
        required
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="inm-tipo" className="text-sm font-medium text-texto">
            Tipo de inmueble
          </label>
          <select
            id="inm-tipo"
            name="tipo"
            defaultValue={valores.tipo}
            className={CLASE_CAMPO}
            required
          >
            <option value="" disabled>
              Selecciona…
            </option>
            {TIPOS_INMUEBLE.map((tipo) => (
              <option key={tipo} value={tipo}>
                {etiqueta(tipo)}
              </option>
            ))}
          </select>
          {errores.tipo !== undefined ? (
            <p role="alert" className="text-sm text-acento-hover">
              {errores.tipo}
            </p>
          ) : null}
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="inm-estado" className="text-sm font-medium text-texto">
            Estado comercial
          </label>
          <select
            id="inm-estado"
            name="estado"
            defaultValue={valores.estado}
            className={CLASE_CAMPO}
            required
          >
            <option value="" disabled>
              Selecciona…
            </option>
            {ESTADOS_INMUEBLE.map((estado) => (
              <option key={estado} value={estado}>
                {etiqueta(estado)}
              </option>
            ))}
          </select>
          {errores.estado !== undefined ? (
            <p role="alert" className="text-sm text-acento-hover">
              {errores.estado}
            </p>
          ) : null}
        </div>
      </div>
      <div className="flex items-center gap-2">
        <input
          id="inm-destacado"
          name="destacado"
          type="checkbox"
          defaultChecked={valores.destacado}
          className="size-4 accent-acento-nogal"
        />
        <label htmlFor="inm-destacado" className="text-sm font-medium text-texto">
          Destacado en portada
        </label>
      </div>
      <Campo
        id="inm-imagen"
        name="imagenUrl"
        etiqueta="Imagen principal (URL https, opcional)"
        type="url"
        maxLength={IMAGEN_MAXIMA}
        placeholder="https://…"
        defaultValue={valores.imagenUrl}
        error={errores.imagenUrl}
      />
    </>
  );
}

/** Características: superficie, habitaciones y baños (todas opcionales). */
export function CamposCaracteristicas({ errores, valores = VACIOS }: CamposProps) {
  return (
    <div className="grid gap-4 sm:grid-cols-3">
      <Campo
        id="inm-superficie"
        name="superficieM2"
        etiqueta="Superficie (m²)"
        type="number"
        min={1}
        inputMode="numeric"
        defaultValue={valores.superficieM2}
        error={errores.superficieM2}
      />
      <Campo
        id="inm-habitaciones"
        name="habitaciones"
        etiqueta="Habitaciones"
        type="number"
        min={0}
        max={200}
        inputMode="numeric"
        defaultValue={valores.habitaciones}
        error={errores.habitaciones}
      />
      <Campo
        id="inm-banios"
        name="banios"
        etiqueta="Baños"
        type="number"
        min={0}
        max={50}
        inputMode="numeric"
        defaultValue={valores.banios}
        error={errores.banios}
      />
    </div>
  );
}

/** Ubicación: dirección, código postal, municipio, provincia y coordenadas. */
export function CamposUbicacion({ errores, valores = VACIOS }: CamposProps) {
  return (
    <>
      <Campo
        id="inm-direccion"
        name="direccion"
        etiqueta="Dirección completa"
        type="text"
        maxLength={DIRECCION_MAXIMA}
        autoComplete="street-address"
        defaultValue={valores.direccion}
        error={errores.direccion}
        required
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <Campo
          id="inm-codigo-postal"
          name="codigoPostal"
          etiqueta="Código postal (opcional)"
          type="text"
          inputMode="numeric"
          maxLength={5}
          autoComplete="postal-code"
          defaultValue={valores.codigoPostal}
          error={errores.codigoPostal}
        />
        <Campo
          id="inm-municipio"
          name="municipio"
          etiqueta="Municipio"
          type="text"
          maxLength={LOCALIDAD_MAXIMA}
          autoComplete="address-level2"
          defaultValue={valores.municipio}
          error={errores.municipio}
          required
        />
      </div>
      <Campo
        id="inm-provincia"
        name="provincia"
        etiqueta="Provincia"
        type="text"
        maxLength={LOCALIDAD_MAXIMA}
        autoComplete="address-level1"
        defaultValue={valores.provincia}
        error={errores.provincia}
        required
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <Campo
          id="inm-lat"
          name="lat"
          etiqueta="Latitud (WGS84, España: 27..44)"
          // Igual que el precio: coma decimal admitida y normalizada en cliente.
          type="text"
          inputMode="decimal"
          defaultValue={valores.lat}
          error={errores.lat}
          required
        />
        <Campo
          id="inm-lng"
          name="lng"
          etiqueta="Longitud (WGS84, España: -19..5)"
          type="text"
          inputMode="decimal"
          defaultValue={valores.lng}
          error={errores.lng}
          required
        />
      </div>
    </>
  );
}

/** Crudo del formulario (inputs no controlados) → datos de validación. */
export function leerDatosFormulario(datos: FormData): DatosInmuebleForm {
  return {
    titulo: String(datos.get('titulo') ?? ''),
    descripcion: String(datos.get('descripcion') ?? ''),
    precio: String(datos.get('precio') ?? ''),
    superficieM2: String(datos.get('superficieM2') ?? ''),
    habitaciones: String(datos.get('habitaciones') ?? ''),
    banios: String(datos.get('banios') ?? ''),
    tipo: String(datos.get('tipo') ?? ''),
    estado: String(datos.get('estado') ?? ''),
    direccion: String(datos.get('direccion') ?? ''),
    codigoPostal: String(datos.get('codigoPostal') ?? ''),
    municipio: String(datos.get('municipio') ?? ''),
    provincia: String(datos.get('provincia') ?? ''),
    lat: String(datos.get('lat') ?? ''),
    lng: String(datos.get('lng') ?? ''),
    imagenUrl: String(datos.get('imagenUrl') ?? ''),
    destacado: datos.get('destacado') === 'on',
  };
}

/** Precarga del form desde el detalle (`GET /api/v1/inmuebles/{id}`). */
export function valoresDesdeDetalle(detalle: InmuebleDetalle): ValoresCampos {
  return {
    titulo: detalle.titulo,
    descripcion: detalle.descripcion ?? '',
    precio: String(detalle.precio),
    superficieM2: detalle.superficieM2 === null ? '' : String(detalle.superficieM2),
    habitaciones: detalle.habitaciones === null ? '' : String(detalle.habitaciones),
    banios: detalle.banios === null ? '' : String(detalle.banios),
    tipo: detalle.tipo,
    estado: detalle.estado,
    direccion: detalle.direccion,
    codigoPostal: detalle.codigoPostal ?? '',
    municipio: detalle.municipio,
    provincia: detalle.provincia,
    lat: String(detalle.lat),
    lng: String(detalle.lng),
    imagenUrl: detalle.imagenUrl ?? '',
    destacado: detalle.destacado,
  };
}
