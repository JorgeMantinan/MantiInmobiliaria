import type { NextConfig } from "next";

/**
 * Cabeceras de seguridad estáticas (M2 de la revisión del PR #15). La CSP ya
 * NO vive aquí: necesita un nonce por petición y se construye en `src/proxy.ts`
 * (issue #39) — tenerla en ambos sitios duplicaría la cabecera.
 */
const cabecerasSeguridad = [
  { key: "X-Frame-Options", value: "DENY" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains" },
];

const nextConfig: NextConfig = {
  async headers() {
    return [
      {
        source: "/(.*)",
        headers: cabecerasSeguridad,
      },
    ];
  },
};

export default nextConfig;
