import { defineConfig, devices } from '@playwright/test';

/**
 * Config E2E del portal (issue #74). NO hay `webServer`: los servidores se
 * levantan fuera de Playwright (local o job `e2e` de CI) porque el smoke
 * necesita el backend Java real (:8080, perfil dev con seed) además del
 * frontend. Ver cómo ejecutarlo en el README del PR.
 *
 * - `E2E_BASE_URL` apunta al `next start` bajo prueba (default :3010).
 * - Sólo chromium: el smoke es de regresión del portal, no matriz de browsers.
 * - Traces en el primer retry para depurar fallos de CI sin grabar siempre.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3010',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});