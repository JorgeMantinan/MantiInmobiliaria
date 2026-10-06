---
name: estructura-nextjs
description: Use when creating or reviewing la estructura del frontend Next.js App Router: carpetas src/app y src/features, server vs client components, data fetching, routing, SEO, Tailwind y manejo de errores.
---

# Estructura del frontend Next.js (App Router)

## Reglas

1. **Árbol canónico.**

```
src/
├── app/
│   ├── (portal)/page.tsx               # home público
│   ├── (portal)/inmueble/[slug]/       # ficha de inmueble
│   ├── (portal)/profesionales/
│   ├── login/
│   ├── admin/(backoffice)/             # layout con sidebar
│   │   ├── inmuebles/ | usuarios/ | swipes/ ...
│   ├── layout.tsx                      # raíz: banner, providers, fuentes
│   ├── not-found.tsx | error.tsx | loading.tsx
│   └── sitemap.ts | robots.ts
├── features/<feature>/
│   ├── components/   # componentes del feature
│   ├── hooks/        # useInmuebles, useApi...
│   ├── types/        # tipos/DTOs del feature
│   └── api.ts        # llamadas al backend Java
├── components/ui/    # primitivas: Button, Input, Card, Badge
├── lib/
│   ├── api.ts        # fetch tipado base + refresh en 401
│   ├── auth.ts       # sesión/JWT
│   └── utils.ts
└── styles/globals.css
```

2. **Server vs client components.**

| Va en server component | Va en client component (`'use client'`) |
|---|---|
| Fetch de datos a la API Java | Estado (`useState`) y efectos |
| `generateMetadata`, SEO, JSON-LD | Handlers de click/submit |
| Layouts, páginas, listas estáticas | Formularios, filtros, modales, mapa Leaflet |
| Consultas a BD/cache del server | Componentes con `useApi` reactivos |

3. **Data fetching.** Los server components hacen `fetch` directo a la API Java en el servidor (URL interna, sin exponer). El cliente usa SIEMPRE el wrapper `lib/api.ts` (fetch tipado, base URL, manejo de 401 → refresh de token → reintento; error tipado).

```ts
// GOOD: cliente
const data = await api.get<PageResponse<Inmueble>>(`/inmuebles?tipo=${tipo}`);

// BAD: fetch crudo con URL repetida y sin 401 handling
const r = await fetch('https://api.manti.../api/v1/inmuebles');
```

4. **Routing y layouts por segmento.** Layout raíz con banner/fuentes/providers; grupo `(portal)` sin layout propio salvo cabecera; `admin/(backoffice)/layout.tsx` con sidebar y guard de sesión. No duplicar chrome en páginas sueltas.

5. **Estática/ISR vs dinámica.**
   - Fichas de inmueble: `generateStaticParams()` + `export const revalidate = 3600` (ISR) + `generateMetadata`.
   - Búsquedas y backoffice: dinámico (`searchParams`, `cache: 'no-store'`).

```ts
export async function generateStaticParams() {
  const { content } = await getInmueblesDestacados();
  return content.map((i) => ({ slug: i.slug }));
}
export const revalidate = 3600;
```

6. **SEO.** Metadata API por ficha: `title`, `description`, Open Graph/Twitter con imagen del inmueble, y JSON-LD (`schema.org` `RealEstateListing`) inyectado en el server component.

7. **Formularios.** En este proyecto se usan **client components + API REST vía `lib/api.ts`** (no server actions): la sesión/JWT vive en el cliente, el backoffice llama a endpoints REST con mutaciones, y así se reutiliza el mismo contrato `api-rest` validado por ProblemDetail. Reservar server actions sólo para casos trivialmente sin sesión.

```tsx
'use client';
export function ProfesionalContactForm() {
  const [sending, setSending] = useState(false);
  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setSending(true);
    await api.post('/profesionales/contacto', Object.fromEntries(new FormData(e.currentTarget)));
  }
  ...
}
```

8. **Tailwind.** Tokens del diseño madera en `theme` (tailwind config / CSS variables): `fondo`, `superficie`, `acento-nogal`, `texto`, `borde`. Nunca repetir hexadecimales hardcoded ni colores fuera de paleta (ver skill diseno-madera).

9. **Errores.** `loading.tsx` (esqueleto), `error.tsx` (retry) y `not-found.tsx` en cada segmento que pueda fallar; en el portal, `not-found.tsx` amable para slugs inexistentes (404 del backend → `notFound()`).

10. **Checklist de nueva página.**
    1. Ruta creada dentro del segmento correcto ((portal) o admin/(backoffice)).
    2. Server component por defecto; `'use client'` sólo si hay interacción.
    3. Datos vía `lib/api.ts`/`api.ts` del feature, con estados loading/error/vacío.
    4. `generateMetadata` + JSON-LD si es público/SEO.
    5. `loading.tsx` y `error.tsx` del segmento.
    6. Componentes en `features/<feature>/components`, primitivas en `components/ui`.
    7. Tailwind con tokens, sin colores hardcoded.
    8. Accesibilidad y contraste AA revisados.

## Checklist de revisión

- [ ] Ruta dentro del segmento correcto y layout reutilizado (sin chrome duplicado)
- [ ] Server component por defecto; cliente sólo con estado/efectos/handlers
- [ ] Todo acceso a datos pasa por `lib/api.ts` o fetch de server a la API Java
- [ ] Fichas ISR con `generateStaticParams` + `revalidate`; búsquedas dinámicas
- [ ] Metadata/OG/JSON-LD presentes en páginas públicas
- [ ] `loading.tsx` / `error.tsx` / `not-found.tsx` del segmento
- [ ] Tailwind con tokens de paleta madera, sin hex repetidos
