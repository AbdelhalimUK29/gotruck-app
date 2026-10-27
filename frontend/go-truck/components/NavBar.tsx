"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { useAuth } from "@/src/auth/useAuth";

function getInitials(
  user: {
    username?: string;
    firstName?: string;
    lastName?: string;
  } | null
): string {
  if (!user) return "";

  if (user.firstName && user.lastName) {
    return `${user.firstName[0]}${user.lastName[0]}`.toUpperCase();
  }

  if (user.firstName) {
    return user.firstName[0].toUpperCase();
  }

  if (user.username) {
    return user.username[0].toUpperCase();
  }

  return "";
}

export default function NavBar() {
  const { isLoading, isAuthenticated, user, companyId, login, register, logout } =
    useAuth();
  const pathname = usePathname();

  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        menuRef.current &&
        !menuRef.current.contains(event.target as Node)
      ) {
        setIsMenuOpen(false);
      }
    }

    document.addEventListener("mousedown", handleClickOutside);

    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, []);

  return (
    <header className="sticky top-0 z-50 border-b border-border bg-card">
      <nav className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <Link
          href="/"
          className="text-lg font-semibold text-text-primary"
        >
          Go<span className="text-primary">Truck</span>
        </Link>

        {isLoading ? (
          <div className="h-8 w-8 animate-pulse rounded-full bg-border" />
        ) : !isAuthenticated ? (
          <div className="flex items-center gap-2">
            <button
              onClick={login}
              className="rounded-lg bg-transparent px-4 py-2 text-sm font-medium text-text-secondary hover:bg-gray-100"
            >
              Login
            </button>

            <button
              onClick={register}
              className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
            >
              Sign Up
            </button>
          </div>
        ) : (
          <div className="relative" ref={menuRef}>
            <button
              onClick={() => setIsMenuOpen((open) => !open)}
              className="flex h-10 w-10 items-center justify-center rounded-full border border-border bg-bg text-sm font-semibold text-text-primary transition-colors hover:bg-gray-100"
              aria-label="Account menu"
              aria-expanded={isMenuOpen}
            >
              {getInitials(user)}
            </button>

            {isMenuOpen && (
              <div className="absolute right-0 mt-2 w-48 rounded-xl border border-border bg-card shadow-sm">
                {user?.firstName || user?.lastName || user?.username ? (
                  <div className="border-b border-border px-4 py-3">
                    <p className="truncate text-sm font-medium text-text-primary">
                      {[user.firstName, user.lastName]
                        .filter(Boolean)
                        .join(" ") || user.username}
                    </p>
                    {user.email && (
                      <p className="truncate text-xs text-text-secondary">
                        {user.email}
                      </p>
                    )}
                  </div>
                ) : null}

                {companyId && (
                  <Link
                    href={`/manager/${companyId}`}
                    onClick={() => setIsMenuOpen(false)}
                    className={`block px-4 py-2 text-sm ${
                      pathname?.startsWith(`/manager/${companyId}`)
                        ? "text-primary font-medium bg-primary/5"
                        : "text-text-primary hover:bg-gray-50"
                    }`}
                  >
                    Company Dashboard
                  </Link>
                )}

                <Link
                  href="/"
                  onClick={() => setIsMenuOpen(false)}
                  className="block px-4 py-2 text-sm text-text-primary hover:bg-gray-50"
                >
                  Profile
                </Link>

                <button
                  onClick={logout}
                  className="block w-full px-4 py-2 text-left text-sm text-error hover:bg-gray-50"
                >
                  Logout
                </button>
              </div>
            )}
          </div>
        )}
      </nav>
    </header>
  );
}
