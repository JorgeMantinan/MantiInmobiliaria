import Link from 'next/link';
import { motion, useMotionValue, useTransform, type PanInfo, type Variants } from 'framer-motion';
import { FotoInmueble } from '@/features/inmuebles/components/foto-inmueble';
import {
  datosDe,
  direccionDe,
  ETIQUETAS_ESTADO,
  ETIQUETAS_TIPO,
  formatoPrecio,
} from '@/features/inmuebles/formato';
import type { InmuebleResumen } from '@/features/inmuebles/types';
import { debeDecidir } from '../arrastre';
import type { DecisionSwipe } from '../types';

type CartaSwipeProps = Readonly<{
  inmueble: InmuebleResumen;
  /** La carta superior es la arrastrable; las demás sólo esperan debajo. */
  esSuperior: boolean;
  /** Decide sobre ESTA carta: el deck valida que siga siendo la superior. */
  onDecidir: (inmuebleId: number, decision: DecisionSwipe) => void;
}>;

/**
 * Salida de la carta: el sentido lo aporta `AnimatePresence` vía `custom`
 * (LIKE → derecha, NOPE → izquierda); la rotación la deriva del propio `x`.
 */
const VARIANTES: Variants = {
  fuera: (direccion: DecisionSwipe) => ({
    x: direccion === 'LIKE' ? 520 : -520,
    opacity: 0,
    transition: { duration: 0.26, ease: 'easeOut' },
  }),
};

const SELLO =
  'absolute top-4 rounded-md bg-superficie/90 px-3 py-1 font-display text-lg font-bold tracking-wide';

/**
 * Carta apilable del deck: la superior se arrastra (derecha = LIKE,
 * izquierda = NOPE) con feedback de sello; las inferiores sólo asoman por
 * detrás. El arrastre es decorativo: siempre existe el par de botones.
 */
export function CartaSwipe({ inmueble, esSuperior, onDecidir }: CartaSwipeProps) {
  const x = useMotionValue(0);
  const rotacion = useTransform(x, [-240, 240], [-14, 14]);
  const opacidadLike = useTransform(x, [40, 150], [0, 1]);
  const opacidadNope = useTransform(x, [-150, -40], [1, 0]);

  function alSoltar(_evento: MouseEvent | TouchEvent | PointerEvent, info: PanInfo): void {
    // Lógica pura en `arrastre.ts`: umbral de 110 px O tirón rápido (>700 px/s).
    const decision = debeDecidir(info.offset.x, info.velocity.x);
    if (decision !== null) onDecidir(inmueble.id, decision);
  }

  return (
    <motion.article
      initial={false}
      exit="fuera"
      variants={VARIANTES}
      style={{ x, rotate: rotacion }}
      animate={
        esSuperior ? { scale: 1, y: 0, opacity: 1 } : { scale: 0.95, y: 24, opacity: 0.92 }
      }
      transition={{ duration: 0.25, ease: 'easeOut' }}
      drag={esSuperior ? 'x' : false}
      dragConstraints={{ left: 0, right: 0 }}
      dragElastic={0.6}
      onDragEnd={alSoltar}
      className={`relative col-start-1 row-start-1 flex flex-col overflow-hidden rounded-lg border border-borde bg-superficie shadow-sm ${
        esSuperior ? 'z-10 cursor-grab active:cursor-grabbing' : 'z-0'
      }`}
    >
      {esSuperior ? <ContenidoCarta inmueble={inmueble} /> : null}

      {/* Sellos de decisión: aparecen según el desplazamiento del arrastre. */}
      <motion.span
        aria-hidden="true"
        style={{ opacity: opacidadLike }}
        className={`${SELLO} right-4 rotate-12 border border-acento-nogal text-acento-nogal`}
      >
        LIKE
      </motion.span>
      <motion.span
        aria-hidden="true"
        style={{ opacity: opacidadNope }}
        className={`${SELLO} left-4 -rotate-12 border border-texto text-texto`}
      >
        NOPE
      </motion.span>
    </motion.article>
  );
}

type ContenidoCartaProps = Readonly<{ inmueble: InmuebleResumen }>;

/** Foto 4:3, badges, título, precio, datos y dirección (tokens madera). */
function ContenidoCarta({ inmueble }: ContenidoCartaProps) {
  const direccion = direccionDe(inmueble);
  const datos = datosDe(inmueble);
  const href = `/inmuebles/${inmueble.id}`;
  const enlace =
    'rounded-md outline-none focus:ring-2 focus:ring-acento-nogal focus:ring-offset-2 focus:ring-offset-superficie';

  return (
    <>
      <FotoInmueble src={inmueble.imagenUrl} alt={`${inmueble.titulo} en ${direccion}`} />

      <div className="flex flex-1 flex-col gap-2 p-5">
        <div className="flex flex-wrap items-center gap-2 text-xs text-texto-suave">
          <span className="rounded-md border border-borde bg-superficie-alta px-2 py-0.5">
            {ETIQUETAS_ESTADO[inmueble.estado]}
          </span>
          <span className="rounded-md border border-borde px-2 py-0.5">
            {ETIQUETAS_TIPO[inmueble.tipo]}
          </span>
        </div>

        <div>
          <h2 className="font-display text-lg text-texto">
            <Link href={href} className={`${enlace} hover:text-acento-hover`}>
              {inmueble.titulo}
            </Link>
          </h2>
          <p className="mt-1 text-xl font-semibold text-acento-nogal">
            {formatoPrecio(inmueble.precio)}
          </p>
          {datos !== '' ? <p className="mt-1 text-sm text-texto-suave">{datos}</p> : null}
          <p className="mt-1 text-sm text-texto">{direccion}</p>
        </div>
      </div>
    </>
  );
}
