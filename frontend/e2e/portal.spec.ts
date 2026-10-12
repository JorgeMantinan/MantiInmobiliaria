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

  // El CTA vive en su propia region; el header tambien enlaza a /login, por
  // eso se acota al bloque del CTA y no al link del header.
  const cta = page.getByRole('region').filter({
    has: page.getByRole('heading', { name: 'Inicia sesión para guardar tus favoritos' }),
  });
  await expect(cta).toBeVisible();
  const ctaLogin = cta.getByRole('link', { name: 'Iniciar sesión' });
  await expect(ctaLogin).toBeVisible();
  await expect(ctaLogin).toHaveAttribute('href', '/login');
  // Sin cartas en pantalla y sin red al deck.
  await expect(page.locator(TARJETAS)).toHaveCount(0);
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

test('home carga sin errores de consola', async ({ page }) => {
  const erroresDeConsola: string[] = [];
  page.on('console', (mensaje) => {
    if (mensaje.type() !== 'error') return;
    const url = mensaje.location().url;
    // Favicon ausente: ruido conocido e inocuo.
    if (/favicon/i.test(url) || /favicon/i.test(mensaje.text())) return;
    // El restore de sesion anonima al montar (session.tsx) llama a
    // /auth/refresh sin cookie y el backend responde 401: la app lo maneja
    // (sigue anonima) y el navegador lo loguea como fallo de recurso.
    if (/\/api\/v1\/auth\/refresh/.test(url)) return;
    // Recursos de terceros (tiles OSM, fotos picsum): si caen es problema del
    // proveedor, no del portal. Los fallos de la API local (localhost) sí se
    // registran: un 404/CORS en /api/v1 es exactamente lo que este smoke caza.
    if (url.startsWith('http') && !/localhost/.test(url)) return;
    erroresDeConsola.push(url !== '' ? `${mensaje.text()} (${url})` : mensaje.text());
  });
  page.on('pageerror', (causa) => erroresDeConsola.push(String(causa)));

  await page.goto('/');
  // Espera a que los destacados se pinten (implica fetch a la API real OK).
  await expect(page.locator('section[aria-labelledby="titulo-destacados"] ' + TARJETAS).first()).toBeVisible();

  expect(erroresDeConsola).toEqual([]);
});