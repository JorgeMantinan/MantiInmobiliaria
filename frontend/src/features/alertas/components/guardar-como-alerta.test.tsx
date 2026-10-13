import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { GuardarComoAlerta } from '@/features/alertas/components/guardar-como-alerta';
import { SesionContext, type ValorSesion } from '@/features/auth/session';
import type { Usuario } from '@/lib/tipos';
import { apiFetch } from '@/lib/api';

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock('@/lib/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/lib/api')>();
  return { ...real, apiFetch: vi.fn() };
});

const USUARIO: Usuario = {
  id: 1,
  email: 'ana@manti.dev',
  nombre: 'Ana',
  rol: 'USER',
  activo: true,
  createdAt: '2026-01-01T10:00:00Z',
};

const CON_SESION: ValorSesion = {
  usuario: USUARIO,
  cargando: false,
  iniciarSesion: vi.fn(),
  cerrarSesion: vi.fn(),
};

const SIN_SESION: ValorSesion = {
  usuario: null,
  cargando: false,
  iniciarSesion: vi.fn(),
  cerrarSesion: vi.fn(),
};

const FILTROS = {
  estado: 'VENTA' as const,
  municipio: 'Madrid',
  precioMax: '250000',
  sort: 'precio_asc' as const,
};

function renderConSesion(valor: ValorSesion | null, ui: ReactNode) {
  if (valor === null) return render(ui);
  return render(<SesionContext.Provider value={valor}>{ui}</SesionContext.Provider>);
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(apiFetch).mockResolvedValue({
    id: 1,
    nombre: 'Pisos Madrid',
    filtros: {},
    activa: true,
    ultimoAvisoEn: null,
    creadoEn: '2026-10-13T10:00:00Z',
    actualizadoEn: '2026-10-13T10:00:00Z',
  });
});

describe('GuardarComoAlerta', () => {
  it('sinSesion_noSePinta_niLlamaALaApi', () => {
    renderConSesion(SIN_SESION, <GuardarComoAlerta filtros={FILTROS} />);

    expect(screen.queryByRole('button', { name: 'Guardar como alerta' })).not.toBeInTheDocument();
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it('sinProveedorDeSesion_noSePinta', () => {
    renderConSesion(null, <GuardarComoAlerta filtros={FILTROS} />);

    expect(screen.queryByRole('button', { name: 'Guardar como alerta' })).not.toBeInTheDocument();
  });

  it('alGuardar_creaLaAlertaConLosFiltrosActuales_yMuestraElAviso', async () => {
    const usuario = userEvent.setup();
    renderConSesion(CON_SESION, <GuardarComoAlerta filtros={FILTROS} />);

    await usuario.click(screen.getByRole('button', { name: 'Guardar como alerta' }));
    await usuario.type(screen.getByLabelText('Nombre de la alerta'), 'Pisos Madrid');
    await usuario.click(screen.getByRole('button', { name: 'Guardar alerta' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Alerta “Pisos Madrid” guardada');
    // sort NO viaja (no es filtro) y los textos numéricos van como número.
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/alertas', {
      method: 'POST',
      body: {
        nombre: 'Pisos Madrid',
        filtros: { estado: 'VENTA', municipio: 'Madrid', precioMax: 250000 },
      },
    });
    expect(screen.getByRole('link', { name: 'Ver mis alertas' })).toHaveAttribute(
      'href',
      '/alertas',
    );
  });

  it('unNombreEnBlanco_anunciaElErrorSinLlamarALaApi', async () => {
    const usuario = userEvent.setup();
    renderConSesion(CON_SESION, <GuardarComoAlerta filtros={FILTROS} />);

    await usuario.click(screen.getByRole('button', { name: 'Guardar como alerta' }));
    // Sólo espacios: pasa el `required` del navegador pero lo recorta el servicio.
    await usuario.type(screen.getByLabelText('Nombre de la alerta'), '   ');
    await usuario.click(screen.getByRole('button', { name: 'Guardar alerta' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Ponle un nombre a la alerta');
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it('unFalloDeLaApi_anunciaElErrorYConservaElFormulario', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error('demasiadas alertas'));
    renderConSesion(CON_SESION, <GuardarComoAlerta filtros={FILTROS} />);

    await usuario.click(screen.getByRole('button', { name: 'Guardar como alerta' }));
    await usuario.type(screen.getByLabelText('Nombre de la alerta'), 'Otra');
    await usuario.click(screen.getByRole('button', { name: 'Guardar alerta' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('demasiadas alertas');
    expect(screen.getByLabelText('Nombre de la alerta')).toBeInTheDocument();
  });

  it('cancelar_cierraElFormularioSinLlamarALaApi', async () => {
    const usuario = userEvent.setup();
    renderConSesion(CON_SESION, <GuardarComoAlerta filtros={FILTROS} />);

    await usuario.click(screen.getByRole('button', { name: 'Guardar como alerta' }));
    await usuario.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByLabelText('Nombre de la alerta')).not.toBeInTheDocument();
    expect(apiFetch).not.toHaveBeenCalled();
  });
});
