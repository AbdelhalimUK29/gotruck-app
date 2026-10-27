"use client";

import { MotionConfig, motion, useReducedMotion } from "framer-motion";

const EASE = [0.16, 1, 0.3, 1] as const;

interface Testimonial {
  initials: string;
  quote: string;
  rating: number;
  name: string;
  company: string;
}

const TESTIMONIALS: Testimonial[] = [
  {
    initials: "SR",
    quote:
      "Go Truck transformed how we handle our fleet. Reservations that used to take days now happen in minutes.",
    rating: 5,
    name: "Sofia Ramirez",
    company: "FreshLine Logistics",
  },
  {
    initials: "MB",
    quote:
      "The booking platform is intuitive and reliable. We scaled from 10 trucks to 40 without hiring extra staff.",
    rating: 5,
    name: "Marcus Bell",
    company: "Bell Transport Co.",
  },
  {
    initials: "AK",
    quote:
      "Real-time tracking and easy invoicing have been a game changer for our operations team.",
    rating: 4,
    name: "Aisha Khan",
    company: "Cedar Freight Group",
  },
];

function Rating({ value }: { value: number }) {
  return (
    <div className="flex justify-center gap-1" aria-label={`${value} out of 5 stars`}>
      {Array.from({ length: 5 }, (_, index) => (
        <span
          key={index}
          aria-hidden
          className={`text-base ${
            index < value ? "text-primary" : "text-text-disabled"
          }`}
        >
          ★
        </span>
      ))}
    </div>
  );
}

function TestimonialCard({
  testimonial,
  index,
}: {
  testimonial: Testimonial;
  index: number;
}) {
  const reducedMotion = useReducedMotion();

  return (
    <motion.article
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.3 }}
      transition={{
        duration: 0.5,
        delay: reducedMotion ? 0 : index * 0.1,
        ease: EASE,
      }}
      whileHover={reducedMotion ? undefined : { y: -8 }}
      className="flex aspect-[3/4] flex-col rounded-2xl border border-border bg-card p-6 shadow-sm transition-shadow duration-300 hover:shadow-lg sm:p-8"
    >
      <div className="flex justify-center">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary text-sm font-bold text-white">
          {testimonial.initials}
        </div>
      </div>

      <div className="mt-5">
        <Rating value={testimonial.rating} />
      </div>

      <blockquote className="mt-4 flex-1">
        <p className="text-center text-base leading-relaxed text-text-secondary">
          &ldquo;{testimonial.quote}&rdquo;
        </p>
      </blockquote>

      <footer className="mt-6 text-center">
        <p className="text-sm font-medium text-text-primary">
          {testimonial.name}
        </p>
        <p className="mt-1 text-xs text-text-secondary">
          {testimonial.company}
        </p>
      </footer>
    </motion.article>
  );
}

export default function TestimonialsSection() {
  return (
    <MotionConfig reducedMotion="user">
      <section className="relative w-full overflow-hidden bg-bg py-28 sm:py-36">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <motion.div
            initial={{ opacity: 0, y: 40 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.5 }}
            transition={{ duration: 0.6, ease: EASE }}
            className="mx-auto max-w-2xl text-center"
          >
            <p className="text-xs font-semibold uppercase tracking-wider text-text-secondary">
              Testimonials
            </p>
            <h2 className="mt-4 text-[clamp(2rem,5vw,3rem)] font-bold leading-tight tracking-tight text-text-primary">
              Customers who{" "}
              <span className="text-primary">trust Go Truck.</span>
            </h2>
            <p className="mt-4 text-lg text-text-secondary">
              From growing startups to national fleets, thousands of
              businesses rely on Go Truck to keep their operations moving.
            </p>
          </motion.div>

          <div className="mt-16 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 md:gap-8">
            {TESTIMONIALS.map((testimonial, index) => (
              <TestimonialCard
                key={testimonial.name}
                testimonial={testimonial}
                index={index}
              />
            ))}
          </div>
        </div>
      </section>
    </MotionConfig>
  );
}