import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiResetPassword } from '@/features/auth/api';
import { FormularioRestablecer } from '@/features/auth/components/formulario-restablecer';
import { MENSAJE_ENLACE_INVALIDO } from '@/features/auth/mensajes';
import { ApiError } from '@/lib/api';

vi.mock('@/features/auth/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/features/auth/api')>();
  return { ...real, apiResetPassword: vi.fn() };
});

const TOKEN_INVALIDO = new ApiError(
  400,
  'Token inválido',
  'El enlace de restablecimiento no es válido, ha caducado o ya ha sido utilizado',
  [],
  'https://api.manti.dev/problems/token-invalido',
);

async function rellenarYEnviar(
  usuario: ReturnType<typeof userEvent.setup>,
  password: string,
  confirmacion: string,
): Promise<void> {
  await usuario.type(screen.getByLabelText('Nueva contraseña'), password);
  await usuario.type(screen.getByLabelText('Repite la contraseña'), confirmacion);
  await usuario.click(screen.getByRole('button', { name: 'Guardar contraseña' }));
}

beforeEach(() => {
  vi.clearAllMocks();
  window.history.replaceState(null, '', '/restablecer');
});

describe('FormularioRestablecer', () => {
  it('captura_el_token_limpia_la_url_y_envia_el_reset', async () => {
    window.history.replaceState(null, '', '/restablecer?token=token-opaco');
    const usuario = userEvent.setup();
    vi.mocked(apiResetPassword).mockResolvedValue(undefined);
    render(<FormularioRestablecer token="token-opaco" />);

    await waitFor(() => {
      // El token de un solo uso no debe quedar en la URL (patrón callback Google).
      expect(window.location.pathname).toBe('/restablecer');
      expect(window.location.search).toBe('');
    });

    await rellenarYEnviar(usuario, 'claveNueva456', 'claveNueva456');

    await waitFor(() => {
      expect(apiResetPassword).toHaveBeenCalledWith({
        token: 'token-opaco',
        password: 'claveNueva456',
      });
    });
    // Éxito: pantalla "Contraseña actualizada" con botón a /login.
    expect(
      await screen.findByRole('heading', { name: 'Contraseña actualizada' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(
      'Tu contraseña se ha cambiado correctamente',
    );
    expect(screen.getByRole('link', { name: 'Ir a iniciar sesión' })).toHaveAttribute(
      'href',
      '/login',
    );
  });

  it('confirmacion_distinta_no_llama_a_la_api_y_pinta_el_error', async () => {
    const usuario = userEvent.setup();
    render(<FormularioRestablecer token="token-opaco" />);

    await rellenarYEnviar(usuario, 'claveNueva456', 'otraClave123');

    expect(await screen.findByRole('alert')).toHaveTextContent('Las contraseñas no coinciden.');
    expect(apiResetPassword).not.toHaveBeenCalled();
  });

  it('password_de_7_caracteres_no_llama_a_la_api', async () => {
    const usuario = userEvent.setup();
    render(<FormularioRestablecer token="token-opaco" />);

    await rellenarYEnviar(usuario, '1234567', '1234567');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'La contraseña debe tener al menos 8 caracteres.',
    );
    expect(apiResetPassword).not.toHaveBeenCalled();
  });

  it('token_invalido_muestra_el_mensaje_y_el_enlace_a_recuperar', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiResetPassword).mockRejectedValue(TOKEN_INVALIDO);
    render(<FormularioRestablecer token="token-caducado" />);

    await rellenarYEnviar(usuario, 'claveNueva456', 'claveNueva456');

    expect(await screen.findByRole('alert')).toHaveTextContent(MENSAJE_ENLACE_INVALIDO);
    expect(screen.getByRole('link', { name: 'Solicitar un enlace nuevo' })).toHaveAttribute(
      'href',
      '/recuperar',
    );
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('ante_un_429_muestra_el_mensaje_de_rate_limit', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiResetPassword).mockRejectedValue(
      new ApiError(429, 'rate-limit', 'Límite de peticiones alcanzado'),
    );
    render(<FormularioRestablecer token="token-opaco" />);

    await rellenarYEnviar(usuario, 'claveNueva456', 'claveNueva456');

    expect(await screen.findByRole('alert')).toHaveTextContent('Demasiadas peticiones.');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('sin_token_muestra_el_enlace_no_valido_sin_formulario', () => {
    render(<FormularioRestablecer />);

    expect(screen.getByRole('alert')).toHaveTextContent(MENSAJE_ENLACE_INVALIDO);
    expect(screen.getByRole('link', { name: 'Solicitar un enlace nuevo' })).toHaveAttribute(
      'href',
      '/recuperar',
    );
    expect(screen.queryByLabelText('Nueva contraseña')).not.toBeInTheDocument();
    expect(apiResetPassword).not.toHaveBeenCalled();
    // No hay nada que limpiar: la URL nunca llevó token.
    expect(window.location.search).toBe('');
  });
});
