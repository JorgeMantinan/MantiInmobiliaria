import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { Header } from "@/components/layout/header";
import { ProveedorSesion } from "@/features/auth/session";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "MantiInmobiliaria",
  description: "Portal de viviendas con mapa geolocalizado, modo swipe y profesionales de confianza.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="es"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="flex min-h-full flex-col bg-fondo text-texto">
        <ProveedorSesion>
          <Header />
          <main className="flex flex-1 flex-col">{children}</main>
        </ProveedorSesion>
      </body>
    </html>
  );
}
