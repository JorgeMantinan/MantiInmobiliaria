---
name: diseno-madera
description: Use when creando o revisando UI con el sistema de diseño madera clara minimalista: tokens Tailwind, botones, inputs, tarjetas de inmueble, tipografía, iconografía y accesibilidad.
---

# Sistema de diseño "madera clara minimalista"

## Reglas

1. **Tokens Tailwind** definidos en el theme (tailwind.config / variables CSS) — nunca hexadecimales repetidos en componentes:

| Token | Valor |
|---|---|
| `fondo` | `#FAF7F2` |
| `superficie` | `#FFFDF9` |
| `superficie-alta` | `#F5EFE6` |
| `acento-nogal` | `#8B6F47` |
| `acento-hover` | `#755C3B` |
| `texto` | `#2C2620` |
| `texto-suave` | `#6B6157` |
| `borde` | `#E4D9C9` |

```ts
// tailwind.config.ts (excerpt)
theme: { extend: { colors: {
  fondo: '#FAF7F2', superficie: '#FFFDF9', 'superficie-alta': '#F5EFE6',
  'acento-nogal': '#8B6F47', 'acento-hover': '#755C3B',
  texto: '#2C2620', 'texto-suave': '#6B6157', borde: '#E4D9C9',
} } }
```

2. **Bordes.** Siempre finos `border` (1px) con color `borde` en tarjetas, inputs y botones secundarios. Prohibido bordes de 2-4px y superficies "flotantes" sin borde.

3. **Sombras.** Máximo `shadow-sm`; preferir `border` + sombra muy sutil. Nunca sombras fuertes (`shadow-xl`, `shadow-2xl`) ni depender de la sombra para separar superficies.

4. **Radios pequeños.** `rounded-md` o `rounded-lg`; prohibido `rounded-3xl`/`rounded-full` salvo avatares.

5. **Tipografía.** Serif sobria para títulos (`font-display`, definida en el theme) + sans para cuerpo. Jerarquía por tamaño/ peso, no por color.

6. **Espaciado generoso.** Padding de tarjetas `p-5`/`p-6`, grids con `gap-6`, secciones con `py-12`/`py-16`. No apretar contenido contra los bordes.

7. **Componentes base.**

```tsx
// Botón primario
className="rounded-md bg-acento-nogal px-4 py-2 text-superficie transition-colors hover:bg-acento-hover"

// Botón secundario
className="rounded-md border border-borde bg-transparent px-4 py-2 text-texto transition-colors hover:bg-superficie-alta"

// Input
className="rounded-md border border-borde bg-superficie px-3 py-2 text-texto outline-none focus:ring-2 focus:ring-acento-nogal"

// Tarjeta inmueble: borde 1px, esquinas suaves, imagen arriba 4:3, precio en nogal
className="overflow-hidden rounded-lg border border-borde bg-superficie transition-colors hover:border-acento-nogal"
```

8. **Iconografía:** `lucide-react` con trazo fino (`strokeWidth={1.5}`). Nunca emojis como iconos ni iconos multicolor.

9. **Fotografía:** ratio **4:3** en tarjetas (`aspect-[4/3] object-cover`), sin filtros chillones ni bordes gruesos en imágenes.

10. **Estados.** `hover`/`active`/`focus` consistentes y sutiles con `transition-colors`; focus visible (`focus:ring-2 focus:ring-acento-nogal`) en todo interactivo.

11. **Accesibilidad.** Contraste mínimo AA sobre fondos claros: verificar combinaciones como `#8B6F47` sobre `#FAF7F2` (texto-suave `#6B6157` para metadatos; no usar acento-nogal para texto pequeño sobre fondo claro si no alcanza 4.5:1).

12. **Anti-patrones prohibidos.** Gradientes chillones; colores fuera de la paleta; bordes 2-4px; sombras fuertes; `rounded-3xl`; emojis como iconos; hexadecimales inline repetidos.

### Ejemplo: tarjeta de inmueble con tokens

```tsx
export function PropertyCard({ property }: PropertyCardProps) {
  return (
    <article className="overflow-hidden rounded-lg border border-borde bg-superficie transition-colors hover:border-acento-nogal">
      <img
        src={property.imagen}
        alt={`${property.titulo} en ${property.ciudad}`}
        className="aspect-[4/3] w-full object-cover"
      />
      <div className="p-5">
        <h3 className="font-display text-lg text-texto">{property.titulo}</h3>
        <p className="text-sm text-texto-suave">{property.ciudad} · {property.m2} m²</p>
        <p className="mt-2 text-acento-nogal">{property.precio.toLocaleString()} €</p>
        <button
          type="button"
          className="mt-4 w-full rounded-md bg-acento-nogal px-4 py-2 text-superficie transition-colors hover:bg-acento-hover"
        >
          Ver detalle
        </button>
      </div>
    </article>
  );
}
```

```tsx
// BAD
<button style={{ background: 'linear-gradient(#f00,#00f)', border: '3px solid red', borderRadius: 30, boxShadow: '0 10px 30px #000' }}>🔥 Ver</button>
```

## Checklist de revisión

- [ ] Sólo tokens de la paleta (sin hex hardcodeados ni colores ajenos)
- [ ] Bordes `border` 1px con `borde` en superficies, tarjetas e inputs
- [ ] Sombras ≤ `shadow-sm`; radios `rounded-md`/`rounded-lg`
- [ ] Títulos con `font-display` (serif) y cuerpo sans; espaciado `p-5/p-6`, `gap-6`
- [ ] Botón primario nogal/crema con hover `acento-hover`; focus ring visible
- [ ] Tarjeta inmueble: borde 1px, imagen 4:3, precio en nogal
- [ ] Iconos lucide-react trazo fino, sin emojis; transiciones con `transition-colors`
- [ ] Contraste AA verificado (p. ej. #8B6F47 sobre #FAF7F2 para texto grande; texto-suave para metadatos)
