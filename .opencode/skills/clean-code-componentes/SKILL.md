---
name: clean-code-componentes
description: Use when writing, reviewing or refactoring componentes React/Next.js in the frontend: clean code, tipado estricto de props, server vs client components, hooks, accesibilidad y anti-patrones.
---

# Clean code en componentes React/Next.js

## Reglas

1. **Tamaño y responsabilidad.** Cada componente tiene una sola responsabilidad y ≤ ~150 líneas y ≤ 5 props. Si supera, extracción: subcomponentes presentacionales o custom hook.

```tsx
// GOOD: tarjeta compuesta
export function PropertyCard({ property }: { property: Property }) { ... }

// BAD: tarjeta que también filtra, paginea y maqueta el layout de la página
export function PropertyCard({ property, filters, page, onFilter, theme, onSort }) { ... }
```

2. **Tipado estricto de props.** Usa `type` (nunca `interface`) para props, props `Readonly`, cero `any` (bloqueado por `@typescript-eslint/no-explicit-any`).

```tsx
// GOOD
type PropertyCardProps = {
  readonly property: Property;
  readonly onSelect?: (id: string) => void;
};

// BAD
interface Props { property: any; onSelect(id: string): void }
```

3. **Composición sobre config-props.** Prefiere `children`, slots y composición a props booleanas/de render que crean árboles condicionales opacos.

```tsx
// GOOD
<CardHeader>...</CardHeader>
<CardBody>...</CardBody>

// BAD
<Card title="..." showHeader={true} renderFooter={() => ...} variant="withTabs" />
```

4. **Server components por defecto.** `'use client'` solo si necesita estado (`useState`), efectos (`useEffect`) o handlers de eventos. Los datos se cargan en el server component y se pasan como props.

```tsx
// GOOD: server component, hace fetch en el servidor
export default async function InmueblePage({ params }: Props) {
  const data = await getInmueble(params.slug);
  return <PropertyCard property={data} />;
}
```

5. **Custom hooks para lógica reutilizable.** Toda lógica de datos/reutilizable vive en hooks `useX` del feature (`useApi`, `useInmuebles`...). Nada de lógica de negocio dentro del JSX.

```tsx
// GOOD
const { data, loading, error } = useApi<Property[]>(`/inmuebles?tipo=${tipo}`);

// BAD: fetch + mapeo + negocio mezclados en el render
function Lista() {
  if (raw.length && raw.filter(p => p.precio > 0).map(...)) ...
}
```

6. **Efectos mínimos.** Nunca `useEffect` para derivar estado: usa una variable o `useMemo`. Las dependencias de `useEffect` son completas (sin suppressions).

```tsx
// GOOD
const total = useMemo(() => items.reduce((s, i) => s + i.precio, 0), [items]);

// BAD
useEffect(() => { setTotal(items.reduce(...)); }, []); // deps incompletas + derivación
```

7. **Naming.** Componentes en PascalCase; hooks `useX`; archivos kebab-case en todo el proyecto (`property-card.tsx`) — mantener la convención existente del repo y no mezclar estilos.

8. **Accesibilidad.** `<label htmlFor>` en todo input, `alt` descriptivo en imágenes, interacción con `<button>` real (nunca `div onClick`), contraste AA mínimo.

```tsx
// BAD
<div onClick={save}>Guardar</div>

// GOOD
<button type="button" onClick={save}>Guardar</button>
```

9. **Errores y estados de lista.** React Error Boundary en el árbol admin/portal y estados `loading` / `vacío` / `error` consistentes en TODA lista (mismo patrón, mismos textos).

10. **Presentacional vs contenedor.** Los componentes de UI (`src/components/ui`, tarjetas) no hacen fetch: reciben datos y callbacks. Los contidores (páginas/features) obtienen datos vía `useApi` y los despliegan.

11. **Anti-patrones prohibidos.**
    - Props drilling > 3 niveles → Context o composición.
    - Render props innecesarias → children/composición.
    - `key={index}` en listas mutables → id estable del inmueble.
    - Mutar props (`props.item.precio = 0`).
    - Estilos inline (`style={{ color: ... }}`) cuando existe Tailwind.

### Ejemplo: tarjeta de inmueble

```tsx
// GOOD
type PropertyCardProps = {
  readonly property: Property;
  readonly onSelect?: (id: string) => void;
};

export function PropertyCard({ property, onSelect }: PropertyCardProps) {
  return (
    <article className="rounded-lg border border-borde bg-superficie p-5 transition-colors">
      <img src={property.imagen} alt={`Inmueble en ${property.ciudad}`} className="aspect-[4/3] w-full rounded-md object-cover" />
      <h3 className="font-display text-lg">{property.titulo}</h3>
      <p className="text-acento-nogal">{property.precio} €</p>
      <button type="button" onClick={() => onSelect?.(property.id)}>Ver detalle</button>
    </article>
  );
}

// BAD
export const Card = ({ item: any, showPrice, onGo, style, ...rest }: any) => {
  useEffect(() => { setLocalPrice(item.precio * 1.1) }, []);
  return <div style={{ background: '#8B6F47' }} onClick={() => onGo(item.id)}>{showPrice && item.precio}</div>;
};
```

## Checklist de revisión

- [ ] Componente ≤ ~150 líneas y ≤ 5 props, una sola responsabilidad
- [ ] Props con `type` + `Readonly`, sin `any` ni `interface`
- [ ] `'use client'` solo donde hay estado/efectos/handlers; datos en server component
- [ ] Sin `useEffect` para derivar estado; dependencias completas
- [ ] Lógica reutilizable en hooks (`useApi`), no en JSX
- [ ] Accesibilidad: labels, alt, `<button>` real, contraste AA
- [ ] Estados loading/vacío/error presentes en toda lista; Error Boundary
- [ ] Sin anti-patrones (key index, mutar props, estilos inline, drilling >3)
