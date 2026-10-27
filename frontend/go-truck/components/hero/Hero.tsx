import HeroScene from "./HeroScene";
import HeroContent from "./HeroContent";

export default function Hero() {
  return (
    <section className="relative min-h-[85vh] w-full overflow-hidden bg-bg">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse 70% 55% at 50% 18%, rgba(254, 215, 170, 0.3), rgba(250, 250, 250, 0) 62%)",
        }}
      />

      <HeroScene />
      <HeroContent />
    </section>
  );
}