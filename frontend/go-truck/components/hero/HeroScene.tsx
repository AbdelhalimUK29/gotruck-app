"use client";

import dynamic from "next/dynamic";

const HeroCanvas = dynamic(() => import("./HeroCanvas"), {
  ssr: false,
  loading: () => <HeroCanvasFallback />,
});

function HeroCanvasFallback() {
  return (
    <div className="flex h-full w-full items-center justify-center">
      <div className="h-10 w-10 animate-pulse rounded-full bg-light-orange" />
    </div>
  );
}

export default function HeroScene() {
  return (
    <div
      aria-hidden
      className="hero-scene-fade-in pointer-events-none absolute inset-0"
    >
      <HeroCanvas />
    </div>
  );
}