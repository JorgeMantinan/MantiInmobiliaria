import type { ReactNode } from 'react';
import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SesionContext, type ValorSesion } from '@/features/auth/session';
import { useDeckSwipe } from '@/features/swipes/hooks/use-deck-swipe';
import type { PaginaSwipes } from '@/features/swipes/types';
import type { InmuebleResumen } from '@/features/inmuebles/types';
import { ApiError, apiFetch } from '@/lib/api';
import type { Usuario } from '@/lib/tipos';

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

const USUARIO: Usuario = {
  id: 1,
  email: 'ana@manti.dev',
  nombre: 'Ana',
  rol: 'USER',
  activo: true,
  createdAt: '2026-01-01T10:00:00Z',
};

const CON_SESION: ValorSesion = {
  usuario: USUARIO,
  cargando: false,
  iniciarSesion: vi.fn(),
  cerrarSesion: vi.fn(),
};

const ENVOLVENTE = {
  wrapper: ({ children }: { children: ReactNode }) => (
    <SesionContext.Provider value={CON_SESION}>{children}</SesionContext.Provider>
  ),
};

function vivienda(id: number, titulo: string): InmuebleResumen {
  return {
    id,
    titulo,
    precio: 200000,
    tipo: 'PISO',
    estado: 'VENTA',
    superficieM2: 96,
    habitaciones: 3,
    banios: 1,
    municipio: 'Madrid',
    provincia: 'Madrid',
    imagenUrl: null,
    destacado: false,
    lat: 40.4168,
    lng: -3.7038,
    createdAt: '2026-01-01T10:00:00Z',
  };
}

function pagina(content: readonly InmuebleResumen[], nextCursor: string | null): PaginaSwipes {
  return { content, nextCursor };
}

function idsDe(cartas: readonly InmuebleResumen[]): number[] {
  return cartas.map((carta) => carta.id);
}

function contarPosts(): number {
  return vi.mocked(apiFetch).mock.calls.filter(([ruta]) => ruta === '/api/v1/swipes').length;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('useDeckSwipe', () => {
  it('la_carta_reinsertada_por_un_post_fallido_no_se_pierde_con_la_pagina_siguiente', async () => {
    const viviendaA = vivienda(1, 'Piso A');
    // GET deck → [A] con cursor; GET deck?cursor → diferido (queda EN VUELO
    // mientras falla el POST de A); POST → rechaza.
    let resolverSiguiente!: (pagina: PaginaSwipes) => void;
    const promesaSiguiente = new Promise<PaginaSwipes>((resolver) => {
      resolverSiguiente = resolver;
    });
    vi.mocked(apiFetch).mockImplementation((ruta: string) => {
      if (ruta.includes('cursor=c1')) return promesaSiguiente;
      if (ruta.startsWith('/api/v1/swipes/deck')) {
        return Promise.resolve(pagina([viviendaA], 'c1'));
      }
      return Promise.reject(new Error('no se ha podido guardar'));
    });

    const { result } = renderHook(() => useDeckSwipe(), ENVOLVENTE);
    await waitFor(() => expect(idsDe(result.current.cartas)).toEqual([1]));

    // Decide la ÚLTIMA carta: lanza el POST y, con el mazo vacío, la página
    // siguiente queda en vuelo.
    act(() => result.current.decidir(1, 'LIKE'));
    // El POST falla: la carta se reinserta mientras esa página sigue en vuelo.
    await waitFor(() => expect(idsDe(result.current.cartas)).toEqual([1]));
    expect(result.current.errorDecision).toBe('no se ha podido guardar');

    // La página siguiente llega DESPUÉS: se FUSIONA con dedupe, no sustituye.
    await act(async () => {
      resolverSiguiente(pagina([vivienda(2, 'Piso B'), vivienda(3, 'Piso C')], null));
      await promesaSiguiente;
    });

    await waitFor(() => expect(idsDe(result.current.cartas)).toEqual([1, 2, 3]));
    // Sin duplicados: la reinsertada (1) convive con la página nueva.
    expect(new Set(idsDe(result.current.cartas)).size).toBe(3);
  });

  it('una_segunda_decision_mientras_la_anterior_aun_sale_no_elimina_otra_carta_sin_post', async () => {
    vi.mocked(apiFetch).mockImplementation((ruta: string) => {
      if (ruta.startsWith('/api/v1/swipes/deck')) {
        return Promise.resolve(pagina([vivienda(1, 'Piso Uno'), vivienda(2, 'Piso Dos')], null));
      }
      return Promise.resolve(undefined);
    });

    const { result } = renderHook(() => useDeckSwipe(), ENVOLVENTE);
    await waitFor(() => expect(idsDe(result.current.cartas)).toEqual([1, 2]));

    // Doble decisión en el MISMO tick: la segunda apunta a la carta 1 con el
    // ref aún sin actualizar (la primera "sigue saliendo"). Sin la guarda de
    // `enVuelo`/id, el segundo slice eliminaría la carta 2 sin su POST.
    act(() => {
      result.current.decidir(1, 'LIKE');
      result.current.decidir(1, 'LIKE');
    });
    expect(idsDe(result.current.cartas)).toEqual([2]);
    expect(contarPosts()).toBe(1);

    // Más adelante, con la carta 1 fuera del mazo, un llamado obsoleto con
    // su id tampoco toca el estado ni repite el POST.
    act(() => result.current.decidir(1, 'NOPE'));
    expect(idsDe(result.current.cartas)).toEqual([2]);
    expect(contarPosts()).toBe(1);

    // La carta 2 sí decide con su propio POST.
    act(() => result.current.decidir(2, 'NOPE'));
    expect(idsDe(result.current.cartas)).toEqual([]);
    expect(contarPosts()).toBe(2);
  });

  it('un_401_en_el_post_de_decision_activa_la_cta_sin_llamadas_posteriores', async () => {
    vi.mocked(apiFetch).mockImplementation((ruta: string) => {
      if (ruta === '/api/v1/swipes') {
        return Promise.reject(new ApiError(401, 'No autorizado', 'Sesión caducada'));
      }
      return Promise.resolve(pagina([vivienda(1, 'Piso Uno')], null));
    });

    const { result } = renderHook(() => useDeckSwipe(), ENVOLVENTE);
    await waitFor(() => expect(idsDe(result.current.cartas)).toEqual([1]));

    act(() => result.current.decidir(1, 'LIKE'));

    await waitFor(() => expect(result.current.sinSesion).toBe(true));
    expect(result.current.errorDecision).toBeNull();
    expect(result.current.cartas).toEqual([]);
    const llamadasTrasCta = vi.mocked(apiFetch).mock.calls.length;

    // Ni página siguiente ni refrescos: con la sesión caída no se llama más.
    await act(async () => {
      await new Promise((resolver) => setTimeout(resolver, 20));
    });
    expect(vi.mocked(apiFetch).mock.calls.length).toBe(llamadasTrasCta);
  });
});
