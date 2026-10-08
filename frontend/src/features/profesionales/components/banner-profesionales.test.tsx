import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { BannerProfesionales } from '@/features/profesionales/components/banner-profesionales';

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

describe('BannerProfesionales', () => {
  it('muestra_el_titulo_del_cta_y_enlaza_a_profesionales', () => {
    render(<BannerProfesionales />);

    expect(
      screen.getByRole('heading', {
        level: 2,
        name: '¿Buscas un profesional? Contacta con los nuestros',
      }),
    ).toBeInTheDocument();

    const enlace = screen.getByRole('link', { name: 'Ver profesionales' });
    expect(enlace).toHaveAttribute('href', '/profesionales');
  });
});
