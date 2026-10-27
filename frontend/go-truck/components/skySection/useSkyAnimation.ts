import { useEffect, useRef, useState } from "react";

import { useDeviceTier, usePrefersReducedMotion } from "@/components/hero/motionPrefs";

const PARALLAX_RANGE = 40;

export interface SkyAnimationState {
  sectionRef: React.RefObject<HTMLDivElement | null>;
  imageWrapRef: React.RefObject<HTMLDivElement | null>;
  isInView: boolean;
  reducedMotion: boolean;
}

export function useSkyAnimation(): SkyAnimationState {
  const sectionRef = useRef<HTMLDivElement>(null);
  const imageWrapRef = useRef<HTMLDivElement>(null);
  const [isInView, setIsInView] = useState(false);
  const reducedMotion = usePrefersReducedMotion();
  const tier = useDeviceTier();
  const parallaxEnabled = !reducedMotion && tier !== "mobile";

  useEffect(() => {
    const section = sectionRef.current;

    if (!section) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        setIsInView(entry.isIntersecting);
      },
      { threshold: 0.25 },
    );

    observer.observe(section);

    return () => {
      observer.disconnect();
    };
  }, []);

  useEffect(() => {
    if (!parallaxEnabled) return;

    const section = sectionRef.current;
    const image = imageWrapRef.current;

    if (!section || !image) return;

    const update = () => {
      const rect = section.getBoundingClientRect();
      const vh = window.innerHeight;
      const travel = vh / 2 + rect.height / 2;
      const progress = (rect.top + rect.height / 2 - vh / 2) / travel;
      const offset = progress * PARALLAX_RANGE;

      image.style.transform = `translate3d(0, ${offset.toFixed(1)}px, 0)`;
    };

    let rafId = 0;

    const onScroll = () => {
      cancelAnimationFrame(rafId);
      rafId = requestAnimationFrame(update);
    };

    const onResize = onScroll;

    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onResize);

    return () => {
      cancelAnimationFrame(rafId);
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onResize);
    };
  }, [parallaxEnabled]);

  return { sectionRef, imageWrapRef, isInView, reducedMotion };
}