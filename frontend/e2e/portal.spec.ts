import { expect, test } from '@playwright/test';

/**
 * Smoke E2E del portal contra backend REAL + seed dev (issue #74).
 * Objetivo: cazar regresiones de contrato API que los tests unitarios (que
 * mockean `apiFetch`) no ven — p. ej. el bug del prefijo /api/v1 (#73).
 * Sin datos frágiles: el seed dev tiene 35 inmuebles y 7 destacados.
 */

/** Tarjeta de `TarjetaInmueble`: <article> con enlaces a /inmuebles/{id}. */
const TARJETAS = 'article:has(a[href^="/inmuebles/"])';

test('home muestra destacados reales', async ({ page }) => {
  await page.goto('/');

  // >= 1 tarjeta con precio (formatoIntl es-ES termina en €).
  const tarjetas = page.locator(TARJETAS);
  await expect(tarjetas.first()).toBeVisible();
  await expect(tarjetas.first().getByText(/€/).first()).toBeVisible();

  // La foto del destacado se sirve por https (FotoInmueble sólo pinta <img>
  // con URL https segura; el fallback no tiene src).
  const foto = page.locator('section[aria-labelledby="titulo-destacados"] img').first();
  await expect(foto).toBeVisible();
  await expect(foto).toHaveAttribute('src', /^https:\/\//);
});

test('listado filtra y navega a la ficha', async ({ page }) => {
  await page.goto('/inmuebles');
  const tarjetas = page.locator(TARJETAS);
  await expect(tarjetas.first()).toBeVisible();

  // Filtra por VENTA (seed dev: 24 en venta) y comprueba el param en la URL.
  await page.selectOption('#filtro-estado', 'VENTA');
  await page.getByRole('button', { name: 'Aplicar filtros' }).click();
  await page.waitForURL(/estado=VENTA/);
  await expect(tarjetas.first()).toBeVisible();

  // Navega a la ficha del primer resultado.
  await tarjetas.first().locator('a[href^="/inmuebles/"]').first().click();
  await page.waitForURL(/\/inmuebles\/\d+/);

  // Ficha: precio + dirección (sección Ubicación) + mapa (region Leaflet).
  const ficha = page.locator('article').filter({ has: page.locator('header h1') });
  await expect(ficha.locator('header').getByText(/€/)).toBeVisible();
  await expect(page.locator('section[aria-labelledby="titulo-ubicacion"] p').first()).toBeVisible();
  // El mapa es client-only (dynamic ssr:false): timeout amplio para el chunk.
  await expect(page.getByRole('region', { name: /Mapa interactivo/ })).toBeVisible({ timeout: 20_000 });
});

test('swipe anonimo pide sesion sin llamar al deck', async ({ page }) => {
  // Registra (y corta) cualquier llamada al deck: un anonimo NO debe llegar.
  const llamadasAlDeck: string[] = [];
  await page.route('**/api/v1/swipes/deck*', (ruta) => {
    llamadasAlDeck.push(ruta.request().url());
    return ruta.fulfill({
      status: 200,
      contentType: 'application/json',
      body: '{"content":[],"nextCursor":null}',
    });
  });

  await page.goto('/swipe');

  // El CTA se localiza por su heading (no por region/aria-label, que un cambio
  // de nombre futuro podria "robar"): el parent directo del h2 es la section.
  const cta = page
    .getByRole('heading', { name: 'Inicia sesión para guardar tus favoritos' })
    .locator('..');
  await expect(cta).toBeVisible();
  const ctaLogin = cta.getByRole('link', { name: 'Iniciar sesión' });
  await expect(ctaLogin).toBeVisible();
  await expect(ctaLogin).toHaveAttribute('href', '/login');
  // Sin cartas en pantalla y sin red al deck.
  await expect(page.locator(TARJETAS)).toHaveCount(0);
  // Margen para que cualquier llamada tardia al deck se registre en la lista.
  // Equivalente robusto a waitForLoadState('networkidle'): en esta pagina el
  // POST /auth/refresh del restore de sesion anonima deja la conexion
  // keep-alive abierta y Playwright no declara networkidle (medido: timeout
  // incluso con la respuesta 401 ya recibida a los 75 ms). Con catch, si
  // networkidle estuviera disponible se espera; si no, no bloquea el test.
  await page.waitForLoadState('networkidle', { timeout: 5_000 }).catch(() => undefined);
  expect(llamadasAlDeck).toHaveLength(0);
});

test('login muestra el formulario y el enlace de recuperacion', async ({ page }) => {
  await page.goto('/login');

  await expect(page.locator('form')).toBeVisible();
  await expect(page.locator('#login-email')).toBeVisible();
  await expect(page.locator('#login-password')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Iniciar sesión' })).toBeVisible();

  const olvide = page.getByRole('link', { name: '¿Olvidaste tu contraseña?' });
  await expect(olvide).toBeVisible();
  await expect(olvide).toHaveAttribute('href', '/recuperar');
});

test('home carga sin respuestas fallidas de la API local', async ({ page }) => {
  const erroresDeConsola: string[] = [];

  // Listener STATUS-AWARE sobre respuestas (revisión PR #77): no se scrapea
  // la consola del navegador, se auditan las respuestas reales de la API
  // local. Cualquier 4xx/5xx en localhost ROMPE el test salvo el UNICO caso
  // tolerado: el 401 del refresh de una sesion anonima al montar (session.tsx
  // llama a /auth/refresh sin cookie; la app lo maneja y sigue anonima).
  // Un 500/403/404 en ese mismo endpoint sí falla: el status manda, no la URL.
  page.on('response', (respuesta) => {
    const url = respuesta.url();
    if (!url.startsWith('http://localhost')) return;
    if (respuesta.status() < 400) return;
    if (respuesta.status() === 401 && url.includes('/api/v1/auth/refresh')) return;
    erroresDeConsola.push(`${respuesta.status()} ${url}`);
  });
  // Errores JS no capturados de la app siguen vigilados aparte.
  page.on('pageerror', (causa) => erroresDeConsola.push(String(causa)));

  await page.goto('/');
  // Espera a que los destacados se pinten (implica fetch a la API real OK).
  await expect(page.locator('section[aria-labelledby="titulo-destacados"] ' + TARJETAS).first()).toBeVisible();

  expect(erroresDeConsola).toEqual([]);
});