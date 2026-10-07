import { ListadoCargando } from '@/features/inmuebles/components/estados-listado';

/** Esqueleto mientras Next resuelve el primer render de `/favoritos`. */
export default function CargandoFavoritos() {
  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6">
      <ListadoCargando />
    </div>
  );
}
