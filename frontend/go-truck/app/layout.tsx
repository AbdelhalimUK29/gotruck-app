import type { Metadata } from "next";
import { Geist } from "next/font/google";
import "./globals.css";
import { KeycloakProvider } from "@/src/auth/keycloakProvider";
import NavBar from "@/components/NavBar";
import Footer from "@/components/footer/Footer";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Go Truck",
  description: "Truck your goals and progress",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} h-full antialiased`}
    >
      <body className="font-sans">
        <KeycloakProvider>
          <NavBar />
          {children}
          <Footer />
        </KeycloakProvider>
      </body>
    </html>
  );
}
