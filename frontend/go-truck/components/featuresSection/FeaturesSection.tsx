"use client";

import Image, { type StaticImageData } from "next/image";
import {
  MotionConfig,
  motion,
  useMotionValue,
  useReducedMotion,
  useSpring,
} from "framer-motion";

import truckForBusiness from "@/public/assets/truck_for_bussiness.jpg";
import truckOnRoad from "@/public/assets/truck_on_road.jpg";

const EASE = [0.16, 1, 0.3, 1] as const;
const MAX_HOVER_TILT_X = 6;
const MAX_HOVER_TILT_Y = 8;

function TiltImage({
  src,
  alt,
  sizes,
}: {
  src: StaticImageData;
  alt: string;
  sizes: string;
}) {
  const reducedMotion = useReducedMotion();

  const rotateX = useSpring(useMotionValue(0), {
    stiffness: 150,
    damping: 20,
  });
  const rotateY = useSpring(useMotionValue(0), {
    stiffness: 150,
    damping: 20,
  });

  const onMouseMove = (event: React.MouseEvent<HTMLDivElement>) => {
    if (reducedMotion) return;

    const rect = event.currentTarget.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width - 0.5;
    const y = (event.clientY - rect.top) / rect.height - 0.5;

    rotateX.set(-y * MAX_HOVER_TILT_X * 2);
    rotateY.set(x * MAX_HOVER_TILT_Y * 2);
  };

  const onMouseLeave = () => {
    rotateX.set(0);
    rotateY.set(0);
  };

  return (
    <motion.div
      onMouseMove={onMouseMove}
      onMouseLeave={onMouseLeave}
      className="h-full w-full"
      style={{ rotateX, rotateY, transformStyle: "preserve-3d" }}
    >
      <Image
        src={src}
        alt={alt}
        fill
        sizes={sizes}
        placeholder="blur"
        className="scale-110 object-cover"
      />
    </motion.div>
  );
}

export default function FeaturesSection() {
  return (
    <MotionConfig reducedMotion="user">
      <section className="relative w-full overflow-hidden bg-bg py-28 sm:py-36">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <motion.div
            initial={{ opacity: 0, y: 40 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.5 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="mx-auto max-w-2xl text-center"
          >
            <p className="text-xs font-semibold uppercase tracking-wider text-text-secondary">
              Features
            </p>
            <h2 className="mt-4 text-[clamp(2.5rem,6vw,4.5rem)] font-bold leading-tight tracking-tight text-text-primary">
              Built for your business,{" "}
              <span className="text-primary">on the road.</span>
            </h2>
            <p className="mt-6 text-lg text-text-secondary">
              From reservation to return, Go Truck keeps your fleet moving
              with tools designed for modern truck rental operations.
            </p>
          </motion.div>

          <div className="mt-16 grid grid-cols-1 gap-8 md:grid-cols-2 md:gap-12">
            <motion.div
              initial={{ opacity: 0, x: -80, rotateY: -8 }}
              whileInView={{ opacity: 1, x: 0, rotateY: 0 }}
              viewport={{ once: true, amount: 0.3 }}
              transition={{ duration: 0.8, ease: EASE }}
              className="aspect-[3/2] w-full overflow-hidden rounded-2xl border border-border bg-card shadow-lg"
            >
              <TiltImage
                src={truckForBusiness}
                alt="Go Truck fleet truck parked for business use"
                sizes="(min-width: 768px) 50vw, 100vw"
              />
            </motion.div>

            <motion.div
              initial={{ opacity: 0, x: 80, rotateY: 8 }}
              whileInView={{ opacity: 1, x: 0, rotateY: 0 }}
              viewport={{ once: true, amount: 0.3 }}
              transition={{ duration: 0.8, delay: 0.15, ease: EASE }}
              className="aspect-[3/2] w-full overflow-hidden rounded-2xl border border-border bg-card shadow-lg"
            >
              <TiltImage
                src={truckOnRoad}
                alt="Go Truck truck driving on the open road"
                sizes="(min-width: 768px) 50vw, 100vw"
              />
            </motion.div>
          </div>
        </div>
      </section>
    </MotionConfig>
  );
}