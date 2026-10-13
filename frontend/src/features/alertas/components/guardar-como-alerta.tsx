'use client';

import Link from 'next/link';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { Boton } from '@/components/ui/boton';
import { useSesionOpcional } from '@/features/auth/session';
import type { FiltrosInmuebles } from '@/features/inmuebles/types';
import { ENLACE_SECUNDARIO } from '@/features/swipes/clases';
import { crearAlerta } from '../api';
import { mensajeDeError, MENSAJES } from '../mensajes';
import { filtrosParaAlerta } from '../serializacion';

type GuardarComoAlertaProps = Readonly<{
  /** Filtros ACTUALES de la URL del listado (los que se guardarán). */
  filtros: FiltrosInmuebles;
}>;

const CLASE_INPUT =
  'mt-1 w-full rounded-md border border-borde bg-superficie px-3 py-2 text-texto outline-none focus:ring-2 focus:ring-acento-nogal';

/**
 * Botón "Guardar como alerta" del listado (issue #96): sólo se pinta con
 * sesión; al pulsarlo pide un nombre y crea la alerta con los filtros
 * actuales, exactamente igual que los manda el listado al backend.
 */
export function GuardarComoAlerta({ filtros }: GuardarComoAlertaProps) {
  const sesion = useSesionOpcional();
  const [abierto, setAbierto] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [guardada, setGuardada] = useState<string | null>(null);
  const [nombre, setNombre] = useState('');

  if (sesion === null || sesion.cargando || sesion.usuario === null) {
    return null;
  }

  function abrir(): void {
    setGuardada(null);
    setError(null);
    setAbierto(true);
  }

  function cancelar(): void {
    setAbierto(false);
    setError(null);
    setNombre('');
  }

  async function guardar(evento: FormEvent<HTMLFormElement>): Promise<void> {
    evento.preventDefault();
    if (guardando) return;
    const nombreLimpio = nombre.trim();
    if (nombreLimpio === '') {
      setError('Ponle un nombre a la alerta.');
      return;
    }
    setGuardando(true);
    setError(null);
    try {
      const creada = await crearAlerta(nombreLimpio, filtrosParaAlerta(filtros));
      setGuardada(creada.nombre);
      setAbierto(false);
      setNombre('');
    } catch (fallo: unknown) {
      setError(mensajeDeError(fallo, MENSAJES.guardar));
    } finally {
      setGuardando(false);
    }
  }

  if (guardada !== null && !abierto) {
    return (
      <div
        role="status"
        className="flex flex-wrap items-center justify-end gap-3 rounded-md border border-borde bg-superficie px-4 py-2 text-sm text-texto"
      >
        <span>
          Alerta “{guardada}” guardada. Te avisaremos de las viviendas nuevas que encajen.
        </span>
        <Link href="/alertas" className={ENLACE_SECUNDARIO}>
          Ver mis alertas
        </Link>
        <Boton variante="secundario" onClick={abrir}>
          Guardar otra
        </Boton>
      </div>
    );
  }

  if (abierto) {
    return (
      <form
        aria-label="Guardar búsqueda como alerta"
        onSubmit={(evento) => void guardar(evento)}
        className="flex w-full flex-col gap-3 rounded-lg border border-borde bg-superficie p-4 sm:max-w-md"
      >
        <div>
          <label htmlFor="nombre-alerta" className="block text-sm font-medium text-texto">
            Nombre de la alerta
          </label>
          <input
            id="nombre-alerta"
            name="nombre"
            required
            maxLength={80}
            autoComplete="off"
            value={nombre}
            onChange={(evento) => setNombre(evento.target.value)}
            placeholder="Pisos en Madrid…"
            className={CLASE_INPUT}
          />
        </div>
        <p className="text-xs text-texto-suave">
          Te avisaremos cuando entre un inmueble nuevo que encaje con estos filtros.
        </p>
        {error !== null ? (
          <p role="alert" className="text-sm text-acento-hover">
            {error}
          </p>
        ) : null}
        <div className="flex justify-end gap-2">
          <Boton variante="secundario" onClick={cancelar} disabled={guardando}>
            Cancelar
          </Boton>
          <Boton type="submit" disabled={guardando}>
            {guardando ? 'Guardando…' : 'Guardar alerta'}
          </Boton>
        </div>
      </form>
    );
  }

  return <Boton variante="secundario" onClick={abrir}>Guardar como alerta</Boton>;
}
