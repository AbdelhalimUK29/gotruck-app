"use client";

import { memo } from "react";
import Image from "next/image";

import animationTruck from "@/public/assets/animation_truck.jpg";
import { useScrollTilt } from "./useScrollTilt";

const TiltCard = memo(function TiltCard() {
  return (
    <div className="relative aspect-[3/2] w-full overflow-hidden rounded-2xl border border-border bg-card shadow-xl">
      <Image
        src={animationTruck}
        alt="Go Truck rental fleet truck driving on the road"
        fill
        sizes="(min-width: 1024px) 1152px, 100vw"
        placeholder="blur"
        className="object-cover"
      />

      <div
        aria-hidden
        className="absolute inset-x-0 top-0 h-2/3 bg-gradient-to-b from-black/50 via-black/20 to-transparent"
      />

      <h2 className="absolute inset-x-0 top-0 px-4 pt-10 text-center sm:pt-14 md:pt-16">
        <span className="mx-auto block max-w-3xl text-3xl font-bold leading-tight tracking-tight text-white drop-shadow-lg sm:text-4xl md:text-5xl lg:text-6xl">
          The right truck,{" "}
          <span className="text-primary">wherever you&apos;re headed.</span>
        </span>
      </h2>
    </div>
  );
});

export default function ScrollTiltImage() {
  const { rotateX, rotateY } = useScrollTilt();

  return (
    <section className="relative w-full overflow-hidden bg-bg py-20 sm:py-24 md:py-28">
      <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
        <div
          className="will-change-transform"
          style={{
            transform: `perspective(1200px) rotateX(${rotateX}deg) rotateY(${rotateY}deg)`,
            transformStyle: "preserve-3d",
          }}
        >
          <TiltCard />
        </div>
      </div>
    </section>
  );
}