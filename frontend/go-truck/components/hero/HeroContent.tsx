"use client";

import Link from "next/link";

import { useHeroAnimationState } from "./heroAnimation";
import { usePrefersReducedMotion } from "./motionPrefs";

const EASE = "cubic-bezier(0.16, 1, 0.3, 1)";

export default function HeroContent() {
  const state = useHeroAnimationState();
  const reducedMotion = usePrefersReducedMotion();
  const revealed =
    reducedMotion || state === "REVEAL" || state === "IDLE";

  return (
    <div className="pointer-events-none relative z-10 flex min-h-[85vh] flex-col items-center justify-center px-4 text-center sm:px-6">
      <h1
        className={`max-w-4xl text-[clamp(3rem,6vw,6rem)] font-bold leading-[1.05] tracking-tight text-text-primary transition-all duration-700 ${
          revealed
            ? "translate-y-0 scale-100 opacity-100"
            : "translate-y-8 scale-95 opacity-0"
        }`}
        style={{ transitionTimingFunction: EASE }}
      >
        Rent the right truck.
        <span className="block text-primary">
          Move your business forward.
        </span>
      </h1>

      <p
        className={`mt-6 max-w-xl text-lg text-text-secondary transition-all duration-700 sm:text-xl ${
          revealed
            ? "translate-y-0 opacity-100"
            : "translate-y-[15px] opacity-0"
        }`}
        style={{
          transitionTimingFunction: EASE,
          transitionDelay: "150ms",
        }}
      >
        Access the trucks you need, when you need them.
        Flexible truck rentals built for modern businesses.
      </p>

      <div
        className={`mt-10 transition-all duration-700 ${
          revealed
            ? "pointer-events-auto translate-y-0 opacity-100"
            : "pointer-events-none translate-y-[10px] opacity-0"
        }`}
        style={{
          transitionTimingFunction: EASE,
          transitionDelay: "300ms",
        }}
      >
        <Link
          href="/manager/onboarding"
          className="rounded-lg bg-primary px-8 py-3.5 text-sm font-medium text-white transition-colors hover:bg-primary-hover"
        >
          Browse Trucks
        </Link>
      </div>
    </div>
  );
}