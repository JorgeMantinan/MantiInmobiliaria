import { describe, expect, it } from 'vitest';
import { GREMIOS_DE_RESERVA } from '@/features/profesionales/types';

/**
 * Fijura LITERAL de `GREMIOS_DE_RESERVA`: fija la constante del frontend
 * (slugs y orden) para que cualquier cambio accidental aquí rompa el test;
 * los formularios pegan estos slugs en `POST /api/v1/solicitudes`.
 *
 * OJO: este test NO lee `V4__profesionales.sql`, sólo compara un literal del
 * cliente contra otro literal del cliente. La sincronía con el catálogo del
 * backend se revisa en el PR que toca la migración.
 */
const CATALOGO_ESPERADO = [
  { slug: 'fontanero', nombre: 'Fontanero' },
  { slug: 'albañil', nombre: 'Albañil' },
  { slug: 'electricista', nombre: 'Electricista' },
  { slug: 'carpintero', nombre: 'Carpintero' },
  { slug: 'maderista', nombre: 'Maderista' },
  { slug: 'soldador', nombre: 'Soldador' },
  { slug: 'pintor', nombre: 'Pintor' },
  { slug: 'jardinero', nombre: 'Jardinero' },
  { slug: 'cerrajero', nombre: 'Cerrajero' },
  { slug: 'gasista', nombre: 'Gasista' },
  { slug: 'techador', nombre: 'Techador' },
  { slug: 'arquitecto', nombre: 'Arquitecto' },
];

describe('GREMIOS_DE_RESERVA', () => {
  it('fija_los_12_oficios_del_catalogo_de_reserva', () => {
    expect(GREMIOS_DE_RESERVA).toEqual(CATALOGO_ESPERADO);
    expect(GREMIOS_DE_RESERVA).toHaveLength(12);
    expect(new Set(GREMIOS_DE_RESERVA.map((gremio) => gremio.slug)).size).toBe(12);
  });
});
