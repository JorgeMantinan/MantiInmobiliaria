import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiForgotPassword } from '@/features/auth/api';
import { FormularioRecuperar } from '@/features/auth/components/formulario-recuperar';
import { MENSAJE_RECUPERACION_ENVIADA } from '@/features/auth/mensajes';
import { ApiError } from '@/lib/api';

vi.mock('@/features/auth/api', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/features/auth/api')>();
  return { ...real, apiForgotPassword: vi.fn() };
});

beforeEach(() => {
  vi.clearAllMocks();
});

describe('FormularioRecuperar', () => {
  it('email_invalido_no_llama_a_la_api_y_pinta_el_error', async () => {
    const usuario = userEvent.setup();
    render(<FormularioRecuperar />);

    await usuario.type(screen.getByLabelText('Email'), 'no-es-email');
    await usuario.click(screen.getByRole('button', { name: 'Enviar enlace' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Escribe un email válido.');
    expect(apiForgotPassword).not.toHaveBeenCalled();
  });

  it('envio_valido_llama_a_la_api_y_muestra_la_confirmacion_generica', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiForgotPassword).mockResolvedValue(undefined);
    render(<FormularioRecuperar />);

    await usuario.type(screen.getByLabelText('Email'), 'ana@example.com');
    await usuario.click(screen.getByRole('button', { name: 'Enviar enlace' }));

    await waitFor(() => {
      expect(apiForgotPassword).toHaveBeenCalledWith({ email: 'ana@example.com' });
    });
    // Confirmación idéntica pase lo que pase: no revela si la cuenta existe.
    expect(await screen.findByRole('status')).toHaveTextContent(MENSAJE_RECUPERACION_ENVIADA);
    expect(screen.getByRole('link', { name: 'Volver a login' })).toHaveAttribute('href', '/login');
  });

  it('ante_un_429_muestra_el_mensaje_de_rate_limit_y_no_la_confirmacion', async () => {
    const usuario = userEvent.setup();
    vi.mocked(apiForgotPassword).mockRejectedValue(
      new ApiError(429, 'rate-limit', 'Límite de peticiones alcanzado'),
    );
    render(<FormularioRecuperar />);

    await usuario.type(screen.getByLabelText('Email'), 'ana@example.com');
    await usuario.click(screen.getByRole('button', { name: 'Enviar enlace' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Demasiadas peticiones.');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toBeEnabled();
  });

  it('muestra_el_enlace_volver_a_login_mientras_se_pide_el_enlace', () => {
    render(<FormularioRecuperar />);

    expect(
      screen.getByRole('link', { name: 'Volver a login' }),
    ).toHaveAttribute('href', '/login');
  });

  it('el_boton_se_deshabilita_mientras_se_envia', async () => {
    const usuario = userEvent.setup();
    let resolver: (() => void) | undefined;
    vi.mocked(apiForgotPassword).mockImplementation(
      () => new Promise<void>((resolve) => (resolver = resolve)),
    );
    render(<FormularioRecuperar />);

    await usuario.type(screen.getByLabelText('Email'), 'ana@example.com');
    await usuario.click(screen.getByRole('button', { name: 'Enviar enlace' }));

    expect(screen.getByRole('button', { name: 'Enviando…' })).toBeDisabled();
    resolver?.();
    await waitFor(() => {
      expect(screen.getByRole('status')).toBeInTheDocument();
    });
  });
});
