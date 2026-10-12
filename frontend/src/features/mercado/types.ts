/**
 * DTOs de `GET /api/v1/mercado` ajustados EXACTOS a `docs/openapi/openapi.yaml`
 * (`MercadoResponse`, `AlquilerMercadoResponse`, `VentaMercadoResponse`).
 *
 * Sólo tipos: la consulta vive en `api.ts`.
 */

/** Alquiler municipal de referencia (SERPAVI), tipología colectiva. */
export type AlquilerZona = Readonly<{
  /** Renta mediana en €/m²·mes; null si la fuente no publica ese indicador. */
  rentaMedianaEurM2Mes: number | null;
  /** Cuantía mediana del contrato en €/mes; null si la fuente no lo publica. */
  cuantiaMedianaEurMes: number | null;
}>;

/** Valor tasado de venta; siempre de ámbito provincial. */
export type VentaZona = Readonly<{
  valorEurM2: number;
  ambito: string;
  provincia: string;
  anio: number;
  trimestre: number;
}>;

/** `GET /api/v1/mercado`. Ambos bloques son opcionales de forma independiente. */
export type PreciosZona = Readonly<{
  codigoINE: string;
  anio: number;
  alquiler: AlquilerZona | null;
  venta: VentaZona | null;
  /** Atribución legal obligatoria del aviso del MIVAU. */
  fuente: string;
}>;
