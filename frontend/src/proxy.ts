import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

/**
 * CSP con nonce por petición (issue #39), siguiendo la guía oficial de esta
 * versión de Next.js (`node_modules/next/dist/docs/01-app/02-guides/
 * content-security-policy.md`): el patrón vive en `proxy.ts` (en Next 16
 * `middleware` está deprecado y renombrado a `proxy`), que genera un nonce
 * fresco por request, lo propaga en `x-nonce` y en la cabecera
 * `Content-Security-Policy`; Next lo extrae del header durante el render y se
 * lo aplica solo a los `<script>` del framework y de las páginas (App Router +
 * Turbopack). Por eso el root layout espera `connection()`: con nonce, las
 * páginas deben renderizarse en dinámico.
 */

/** Igual que `lib/auth.ts`: la API en local escucha en :8080. */
const API_BASE = (process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080').replace(
  /\/+$/,
  '',
);
const ORIGEN_API = new URL(API_BASE).origin;

/**
 * Directives de la CSP. `style-src-attr 'unsafe-inline'` es deliberado: la
 * mini-tarjeta del mapa (`mapa-tarjeta`) y framer-motion emiten atributos
 * `style="…"` en el HTML del SSR y un nonce no puede aplicarse a atributos
 * (CSP3); sin este directive el navegador descartaría esas posiciones.
 * `img-src https:` cubre las fotos de la API y los tiles OSM/CARTO.
 */
export function construirCsp(nonce: string, esDev: boolean): string {
  const csp = `
    default-src 'self';
    script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${esDev ? " 'unsafe-eval'" : ''};
    style-src 'self';
    style-src-attr 'unsafe-inline';
    img-src 'self' https: data: blob: ${ORIGEN_API};
    font-src 'self';
    connect-src 'self' ${ORIGEN_API};
    object-src 'none';
    base-uri 'self';
    form-action 'self';
    frame-ancestors 'none';
    upgrade-insecure-requests;
`;
  return csp.replace(/\s{2,}/g, ' ').trim();
}

export function proxy(request: NextRequest) {
  // Nonce impredecible y de un solo uso por petición (crypto.randomUUID).
  const nonce = Buffer.from(crypto.randomUUID()).toString('base64');
  const esDev = process.env.NODE_ENV === 'development';
  const contentSecurityPolicy = construirCsp(nonce, esDev);

  const requestHeaders = new Headers(request.headers);
  requestHeaders.set('x-nonce', nonce);
  requestHeaders.set('Content-Security-Policy', contentSecurityPolicy);

  const response = NextResponse.next({
    request: { headers: requestHeaders },
  });
  response.headers.set('Content-Security-Policy', contentSecurityPolicy);

  return response;
}

export const config = {
  matcher: [
    /*
     * Sólo documentos HTML: sin APIs, assets estáticos ni prefetch (la guía de
     * CSP recomienda no gastar nonces en peticiones que no son documento).
     */
    {
      source: '/((?!api|_next/static|_next/image|favicon.ico|sitemap.xml|robots.txt).*)',
      missing: [
        { type: 'header', key: 'next-router-prefetch' },
        { type: 'header', key: 'purpose', value: 'prefetch' },
      ],
    },
  ],
};
