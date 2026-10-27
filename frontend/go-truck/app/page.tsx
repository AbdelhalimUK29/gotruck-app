import type { Metadata } from "next";

import Hero from "@/components/hero/Hero";
import ScrollTiltImage from "@/components/scrollTilt/ScrollTiltImage";
import SkySection from "@/components/skySection/SkySection";
import FeaturesSection from "@/components/featuresSection/FeaturesSection";
import TestimonialsSection from "@/components/testimonials/TestimonialsSection";
import CtaSection from "@/components/ctaSection/CtaSection";

export const metadata: Metadata = {
  title: "Go Truck | Modern Truck Rental SaaS",
  description: "Go Truck is a comprehensive SaaS platform for renting trucks. Manage your fleet, handle reservations, and streamline operations with our intuitive platform.",
};

export default function Home() {
  return (
    <main>
      <Hero />
      <ScrollTiltImage />
      <SkySection />
      <FeaturesSection />
      <TestimonialsSection />
      <CtaSection />
    </main>
  );
}