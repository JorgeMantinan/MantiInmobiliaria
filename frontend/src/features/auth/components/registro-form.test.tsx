import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiRegister } from '@/features/auth/api';
import { RegistroForm } from '@/features/auth/components/registro-form';
import { SesionContext } from '@/features/auth/session';
import type { ValorSesion } from '@/features/auth/session';
import { ApiError } from '@/lib/api';
import type { AuthResponse, Usuario } from '@/features/auth/types';

const navegacion = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: navegacion.push, replace: navegacion.replace, prefetch: vi.fn() }),
}));

vi.mock('@/features/auth/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/features/auth/api')>();
  return { ...real, apiRegister: vi.fn() };
});

const USUARIO: Usuario = {
  id: 7,
  email: 'ana@example.com',
  nombre: 'Ana Pérez',
  rol: 'USER',
  activo: true,
  createdAt: '2026-01-01T10:00:00Z',
};

const SESION: AuthResponse = { accessToken: 'token', expiresIn: 900, user: USUARIO };

function valorDeSesion(): ValorSesion {
  return {
    usuario: null,
    cargando: false,
    iniciarSesion: vi.fn(),
    cerrarSesion: vi.fn(async () => {}),
  };
}

function renderizarRegistro(valor: ValorSesion = valorDeSesion()) {
  const utils = render(
    <SesionContext.Provider value={valor}>
      <RegistroForm />
    </SesionContext.Provider>,
  );
  return { ...utils, valor };
}

type Datos = Readonly<{ nombre?: string; email?: string; password?: string; confirmacion?: string }>;

async function rellenar(
  usuario: ReturnType<typeof userEvent.setup>,
  datos: Datos,
): Promise<void> {
  if (datos.nombre !== undefined) {
    await usuario.type(screen.getByLabelText('Nombre'), datos.nombre);
  }
  if (datos.email !== undefined) {
    await usuario.type(screen.getByLabelText('Email'), datos.email);
  }
  if (datos.password !== undefined) {
    await usuario.type(screen.getByLabelText('Contraseña'), datos.password);
  }
  if (datos.confirmacion !== undefined) {
    await usuario.type(screen.getByLabelText('Repite la contraseña'), datos.confirmacion);
  }
}

async function enviar(usuario: ReturnType<typeof userEvent.setup>): Promise<void> {
  await usuario.click(screen.getByRole('button', { name: 'Crear cuenta' }));
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('RegistroForm', () => {
  it('submit_invalido_no_llama_a_apiRegister_y_pinta_los_errores', async () => {
    const usuario = userEvent.setup();
    const { valor } = renderizarRegistro();

    await rellenar(usuario, {
      email: 'no-es-un-email',
      password: 'corta',
      confirmacion: 'distinta',
    });
    await enviar(usuario);

    expect(apiRegister).not.toHaveBeenCalled();
    expect(valor.iniciarSesion).not.toHaveBeenCalled();
    expect(navegacion.push).not.toHaveBeenCalled();
    expect(await screen.findByText('Escribe tu nombre.')).toBeInTheDocument();
    expect(screen.getByText('Escribe un email válido.')).toBeInTheDocument();
    expect(screen.getByText('La contraseña debe tener al menos 8 caracteres.')).toBeInTheDocument();
    expect(screen.getByText('Las contraseñas no coinciden.')).toBeInTheDocument();
  });

  it('con_email_ya_registrado_muestra_el_mensaje_de_conflicto', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiRegister).mockRejectedValue(
      new ApiError(409, 'conflicto', 'El email ya está registrado'),
    );
    const { valor } = renderizarRegistro();

    await rellenar(usuario, {
      nombre: 'Ana Pérez',
      email: 'ana@example.com',
      password: 'secreto123',
      confirmacion: 'secreto123',
    });
    await enviar(usuario);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Ese email ya está registrado.',
    );
    expect(valor.iniciarSesion).not.toHaveBeenCalled();
    expect(navegacion.push).not.toHaveBeenCalled();
  });

  it('con_datos_validos_registra_inicia_sesion_y_navega_al_inicio', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiRegister).mockResolvedValue(SESION);
    const { valor } = renderizarRegistro();

    await rellenar(usuario, {
      nombre: 'Ana Pérez',
      email: 'ana@example.com',
      password: 'secreto123',
      confirmacion: 'secreto123',
    });
    await enviar(usuario);

    await waitFor(() => {
      expect(apiRegister).toHaveBeenCalledWith({
        nombre: 'Ana Pérez',
        email: 'ana@example.com',
        password: 'secreto123',
      });
    });
    expect(valor.iniciarSesion).toHaveBeenCalledWith(SESION);
    expect(navegacion.push).toHaveBeenCalledWith('/');
  });
});
