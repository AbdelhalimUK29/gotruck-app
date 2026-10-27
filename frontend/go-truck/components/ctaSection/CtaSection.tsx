"use client";

import Link from "next/link";
import Image from "next/image";
import {
  MotionConfig,
  motion,
  useReducedMotion,
  useScroll,
  useTransform,
} from "framer-motion";
import { useRef } from "react";

import getYourTruck from "@/public/assets/get_your_truck.jpg";

const EASE = [0.16, 1, 0.3, 1] as const;

export default function CtaSection() {
  const reducedMotion = useReducedMotion();
  const sectionRef = useRef<HTMLElement>(null);

  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ["start end", "end start"],
  });

  const imageY = useTransform(scrollYProgress, [0, 1], [-40, 40]);
  const imageScale = useTransform(scrollYProgress, [0, 0.5, 1], [1.08, 1, 1.08]);

  return (
    <MotionConfig reducedMotion="user">
      <section
        ref={sectionRef}
        className="relative w-full overflow-hidden bg-bg py-28 sm:py-36"
      >
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-1 items-center gap-12 lg:grid-cols-2 lg:gap-16">
            <motion.div
              initial={reducedMotion ? { opacity: 1 } : { opacity: 0, x: -40 }}
              whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true, amount: 0.4 }}
              transition={{ duration: 0.7, ease: EASE }}
              className="text-center lg:text-left"
            >
              <p className="text-xs font-semibold uppercase tracking-wider text-text-secondary">
                Get Started
              </p>
              <h2 className="mt-4 text-[clamp(2.5rem,6vw,4rem)] font-bold leading-tight tracking-tight text-text-primary">
                Get your truck,{" "}
                <span className="text-primary">on the road today.</span>
              </h2>
              <p className="mx-auto mt-6 max-w-xl text-lg text-text-secondary lg:mx-0">
                Join thousands of businesses that book the trucks they need
                in minutes — not days. Flexible rentals, transparent
                pricing, and a fleet ready when you are.
              </p>

              <div className="mt-8 flex flex-col items-center gap-4 sm:flex-row sm:justify-center lg:justify-start">
                <Link
                  href="/manager/onboarding"
                  className="inline-flex items-center rounded-lg bg-primary px-8 py-3.5 text-sm font-medium text-white transition-colors hover:bg-primary-hover"
                >
                  Get Started
                </Link>
                <Link
                  href="#"
                  className="inline-flex items-center rounded-lg border border-border bg-transparent px-8 py-3.5 text-sm font-medium text-text-primary transition-colors hover:bg-gray-50"
                >
                  Talk to Sales
                </Link>
              </div>
            </motion.div>

            <motion.div
              initial={reducedMotion ? { opacity: 1 } : { opacity: 0, y: 40 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.4 }}
              transition={{ duration: 0.8, delay: 0.1, ease: EASE }}
              className="relative"
            >
              <motion.div
                style={
                  reducedMotion
                    ? undefined
                    : { y: imageY, scale: imageScale }
                }
                className="relative aspect-[3/4] w-full overflow-hidden rounded-2xl border border-border bg-card shadow-lg"
              >
                <Image
                  src={getYourTruck}
                  alt="Go Truck ready for your business"
                  fill
                  sizes="(min-width: 1024px) 50vw, 100vw"
                  placeholder="blur"
                  className="object-cover"
                />
              </motion.div>

              <div className="pointer-events-none absolute inset-0 rounded-2xl bg-gradient-to-t from-black/30 via-transparent to-transparent" />
            </motion.div>
          </div>
        </div>
      </section>
    </MotionConfig>
  );
}