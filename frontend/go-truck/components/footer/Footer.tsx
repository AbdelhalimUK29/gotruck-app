"use client";

import Link from "next/link";
import { MotionConfig, motion } from "framer-motion";

import { useAuth } from "@/src/auth/useAuth";
import SocialIcon, {
  type SocialIconName,
} from "@/components/footer/SocialIcon";
import {
  COMPANY_LINKS,
  LEGAL_LINKS,
  PRODUCT_LINKS,
  type FooterLinkGroup,
} from "@/components/footer/footerLinks";

const EASE = [0.16, 1, 0.3, 1] as const;

const SOCIAL_LINKS: { name: SocialIconName; href: string; label: string }[] = [
  { name: "github", href: "https://github.com", label: "GitHub" },
  { name: "linkedin", href: "https://linkedin.com", label: "LinkedIn" },
  { name: "twitter", href: "https://twitter.com", label: "Twitter" },
];

function LinkColumn({ group, index }: { group: FooterLinkGroup; index: number }) {
  return (
    <motion.nav
      aria-label={`${group.title} links`}
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.4 }}
      transition={{
        duration: 0.5,
        delay: index * 0.05,
        ease: EASE,
      }}
    >
      <h3 className="text-sm font-semibold text-text-primary">
        {group.title}
      </h3>
      <ul className="mt-4 space-y-3">
        {group.links.map((link) => (
          <li key={link.label}>
            <Link
              href={link.href}
              className="text-sm text-text-secondary transition-colors hover:text-text-primary"
            >
              {link.label}
            </Link>
          </li>
        ))}
      </ul>
    </motion.nav>
  );
}

export default function Footer() {
  const { isLoading, isAuthenticated, login, register, logout } = useAuth();

  return (
    <MotionConfig reducedMotion="user">
      <footer
        role="contentinfo"
        className="w-full border-t border-border bg-bg"
      >
        <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
          <div className="grid grid-cols-1 gap-10 sm:grid-cols-2 lg:grid-cols-4 lg:gap-8">
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.4 }}
              transition={{ duration: 0.5, ease: EASE }}
            >
              <Link
                href="/"
                className="text-lg font-semibold text-text-primary"
              >
                Go<span className="text-primary">Truck</span>
              </Link>
              <p className="mt-4 max-w-xs text-sm text-text-secondary">
                Rent the right truck. Move your business forward.
              </p>
              <ul className="mt-6 flex items-center gap-4">
                {SOCIAL_LINKS.map((social) => (
                  <li key={social.name}>
                    <motion.a
                      href={social.href}
                      aria-label={social.label}
                      target="_blank"
                      rel="noopener noreferrer"
                      whileHover={{ scale: 1.15 }}
                      className="block text-text-secondary transition-colors hover:text-text-primary"
                    >
                      <SocialIcon name={social.name} />
                    </motion.a>
                  </li>
                ))}
              </ul>
            </motion.div>

            <LinkColumn group={PRODUCT_LINKS} index={1} />
            <LinkColumn group={COMPANY_LINKS} index={2} />
            <LinkColumn group={LEGAL_LINKS} index={3} />
          </div>
        </div>

        <div className="border-t border-border">
          <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-4 px-4 py-6 sm:px-6 md:flex-row lg:px-8">
            <p className="text-xs text-text-secondary">
              &copy; 2026 GoTruck. All rights reserved.
            </p>

            {isLoading ? (
              <div className="h-8 w-24 animate-pulse rounded-lg bg-border" />
            ) : !isAuthenticated ? (
              <div className="flex items-center gap-2">
                <button
                  onClick={login}
                  className="rounded-lg bg-transparent px-4 py-2 text-sm font-medium text-text-secondary transition-colors hover:bg-gray-100"
                >
                  Login
                </button>
                <button
                  onClick={register}
                  className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-hover"
                >
                  Sign Up
                </button>
              </div>
            ) : (
              <button
                onClick={logout}
                className="rounded-lg bg-transparent px-4 py-2 text-sm font-medium text-error transition-colors hover:bg-gray-100"
              >
                Logout
              </button>
            )}
          </div>
        </div>
      </footer>
    </MotionConfig>
  );
}
