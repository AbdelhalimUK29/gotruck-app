"use client";

import Image from "next/image";

import truckSky from "@/public/assets/truck_sky.jpg";
import { useSkyAnimation } from "./useSkyAnimation";

const EASE = "cubic-bezier(0.16, 1, 0.3, 1)";

export default function SkySection() {
  const { sectionRef, imageWrapRef, isInView, reducedMotion } =
    useSkyAnimation();
  const revealed = reducedMotion || isInView;

  return (
    <section
      ref={sectionRef}
      className="relative h-[420px] w-full overflow-hidden bg-bg sm:h-[500px] lg:h-[600px]"
    >
      <div
        ref={imageWrapRef}
        aria-hidden
        className="absolute inset-0 will-change-transform"
      >
        <Image
          src={truckSky}
          alt=""
          fill
          sizes="100vw"
          placeholder="blur"
          className="scale-110 object-cover"
        />
      </div>

      <div
        aria-hidden
        className="absolute inset-0 bg-gradient-to-b from-black/50 via-black/25 to-transparent"
      />

      <div className="relative z-10 flex h-full items-center justify-center px-4 text-center sm:px-6">
        <div
          className={`max-w-3xl transition-all duration-700 ${
            revealed
              ? "translate-y-0 opacity-100"
              : "translate-y-8 opacity-0"
          }`}
          style={{ transitionTimingFunction: EASE }}
        >
          <h2 className="text-4xl font-bold leading-tight tracking-tight text-white drop-shadow-lg sm:text-5xl md:text-6xl">
            Built for fleets that{" "}
            <span className="text-light-orange">never slow down.</span>
          </h2>

          <p
            className={`mx-auto mt-6 max-w-xl text-lg text-white/85 transition-all duration-700 sm:mt-8 sm:text-xl ${
              revealed
                ? "translate-y-0 opacity-100"
                : "translate-y-[15px] opacity-0"
            }`}
            style={{
              transitionTimingFunction: EASE,
              transitionDelay: revealed ? "150ms" : "0ms",
            }}
          >
            Manage reservations, track vehicles, and scale your truck
            rental operations from a single platform. Go Truck gives your
            business the tools to move faster.
          </p>
        </div>
      </div>
    </section>
  );
}