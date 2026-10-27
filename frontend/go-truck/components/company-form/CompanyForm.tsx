"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { MotionConfig, motion } from "framer-motion";

import { useAuth } from "@/src/auth/useAuth";
import { ApiError } from "@/src/api/apiClient";
import { companyService } from "@/src/services/companyService";
import { userService } from "@/src/services/userService";
import type { Company } from "@/types/company";
import FieldInput from "@/components/company-form/FieldInput";

type CompanyFieldErrors = Partial<Record<keyof Company, string>>;

const EASE = [0.16, 1, 0.3, 1] as const;

const MAX_NAME = 255;
const MAX_WEBSITE = 255;
const MAX_EMAIL = 255;
const MAX_LOCATION = 255;
const MAX_CITY = 100;
const MAX_COUNTRY = 100;

const POLL_INTERVAL_MS = 3000;
const MAX_POLL_ATTEMPTS = 100;

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const EMPTY_VALUES: Company = {
  name: "",
  website: "",
  email: "",
  location: "",
  city: "",
  country: "",
};

type SubmitStatus = "idle" | "submitting" | "pending" | "success" | "failed_manual";

function validate(values: Company): CompanyFieldErrors {
  const errors: CompanyFieldErrors = {};
  const name = values.name.trim();
  const email = values.email.trim();

  if (!name) {
    errors.name = "Company name is required";
  } else if (name.length > MAX_NAME) {
    errors.name = `Company name must not exceed ${MAX_NAME} characters`;
  }

  if (values.website && values.website.trim().length > MAX_WEBSITE) {
    errors.website = `Website must not exceed ${MAX_WEBSITE} characters`;
  }

  if (!email) {
    errors.email = "Company email is required";
  } else if (!EMAIL_REGEX.test(email)) {
    errors.email = "Email must be valid";
  } else if (email.length > MAX_EMAIL) {
    errors.email = `Email must not exceed ${MAX_EMAIL} characters`;
  }

  if (values.location && values.location.trim().length > MAX_LOCATION) {
    errors.location = `Location must not exceed ${MAX_LOCATION} characters`;
  }

  if (values.city && values.city.trim().length > MAX_CITY) {
    errors.city = `City must not exceed ${MAX_CITY} characters`;
  }

  if (values.country && values.country.trim().length > MAX_COUNTRY) {
    errors.country = `Country must not exceed ${MAX_COUNTRY} characters`;
  }

  return errors;
}

export default function CompanyForm() {
  const router = useRouter();
  const { isLoading: authLoading, isAuthenticated, login, user, refreshToken,companyId } = useAuth();

  const [values, setValues] = useState<Company>(EMPTY_VALUES);
  const [errors, setErrors] = useState<CompanyFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [status, setStatus] = useState<SubmitStatus>("idle");
  const [pollingCompanyId, setPollingCompanyId] = useState<string | null>(null);
  

  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const pollAttemptsRef = useRef(0);
  const currentPollingIdRef = useRef<string | null>(null);
  // Idempotency key should be stable for the same submission attempt
  // Generate once on mount and reuse for retries (network errors, validation fixes)
  const idempotencyKeyRef = useRef<string>(crypto.randomUUID());

  const stopPolling = () => {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  };

  async function pollProvisioningStatus() {
    // Ignore responses from stale polling sessions (race guard)
    const activePollId = currentPollingIdRef.current;
    if (!activePollId) return;

    try {
      const response = await companyService.getProvisioningStatus();
      const provisioningStatus = response.provisioningStatus;

      if (provisioningStatus === "SUCCEEDED") {
        stopPolling();
        setPollingCompanyId(response.id);
        setStatus("success");
        return;
      }

      if (provisioningStatus === "COMPENSATED") {
        stopPolling();
        setPollingCompanyId(null);
        router.push("/");
        return;
      }

      if (provisioningStatus === "FAILED_MANUAL") {
        stopPolling();
        setPollingCompanyId(null);
        setStatus("failed_manual");
        return;
      }
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 401 || error.status === 403) {
          stopPolling();
          setPollingCompanyId(null);
          login();
          return;
        }

        if (error.status === 404) {
          stopPolling();
          setPollingCompanyId(null);
          router.push("/");
          return;
        }
      }
    }
  }

  const startPolling = useCallback((companyId: string) => {
    
    currentPollingIdRef.current = companyId;
    setPollingCompanyId(companyId);
    setStatus("pending");
    pollAttemptsRef.current = 0;
    stopPolling();

    void pollProvisioningStatus();

    pollTimerRef.current = setInterval(() => {
      pollAttemptsRef.current += 1;
      if (pollAttemptsRef.current >= MAX_POLL_ATTEMPTS) {
        stopPolling();
        setPollingCompanyId(null);
        currentPollingIdRef.current = null;
        router.push("/");
        return;
      }
      void pollProvisioningStatus();
    }, POLL_INTERVAL_MS);
    // router stable; pollProvisioningStatus is intentionally local (circular dep)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [router]);

  useEffect(() => {
    return () => {
      stopPolling();
    };
  }, []);

  // Auto-redirect to company dashboard after showing success
  useEffect(() => {
    if (pollingCompanyId && status === "success") {
      const timer = setTimeout(async () => {
        // Refresh token to pick up updated company_id claim after company creation
        try { await refreshToken(); } catch { /* non-blocking */ }
        router.push(`/manager/${pollingCompanyId}`);
      }, 2000);
      return () => clearTimeout(timer);
    }
  }, [pollingCompanyId, status, router, refreshToken]);

  // Fetch user profile to check for existing company and start polling if needed
  useEffect(() => {
    if (!isAuthenticated || !user?.id || companyId) return;

    const keycloakId = user.id;
    let cancelled = false;

    async function fetchProfile() {
      try {
        const profile = await userService.getUserProfileByKeycloakId(keycloakId);
        if (cancelled) return;
        // If user already has a company, start polling its provisioning status
        if (profile.companyId != null) {
          startPolling(String(profile.companyId));
        }
      } catch (error) {
        console.error("[CompanyForm] Failed to fetch user profile:", error);
      }
    }

    void fetchProfile();

    return () => {
      cancelled = true;
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated, user?.id]);

  const setField = (field: keyof Company) => (value: string) => {
    setValues((prev) => ({ ...prev, [field]: value }));
    setErrors({});
    setFormError(null);
  };

  const hasErrors = useMemo(
    () => Object.values(errors).some(Boolean),
    [errors]
  );

  async function handleSubmit() {
    if (status === "submitting") return;

    const nextErrors = validate(values);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).some((key) => nextErrors[key as keyof Company])) {
      return;
    }

    setFormError(null);
    setStatus("submitting");

    // Use stable idempotency key from ref - same key for retries of the same attempt
    const idempotencyKey = idempotencyKeyRef.current;

    try {
      const response = await companyService.create(values, idempotencyKey);
      const provisioningStatus = response.provisioningStatus;

      if (provisioningStatus === "SUCCEEDED") {
        setPollingCompanyId(response.id);
        setStatus("success");
        return;
      }

      if (provisioningStatus === "COMPENSATED") {
        router.push("/");
        return;
      }

      if (provisioningStatus === "FAILED_MANUAL") {
        setStatus("failed_manual");
        return;
      }

      startPolling(response.id);
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 401 || error.status === 403) {
          setStatus("idle");
          login();
          return;
        }
        setErrors((prev) => ({
          ...prev,
          ...(error.fieldErrors as CompanyFieldErrors),
        }));
        setFormError(error.message);
      } else {
        setFormError("Something went wrong. Please try again.");
      }
      setStatus("idle");
    }
  }

  if (authLoading) {
    return (
      <div className="mx-auto flex max-w-xl items-center justify-center py-24">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-border border-t-primary" />
      </div>
    );
  }

  if (!isAuthenticated && !companyId) {
    return (
      <div className="mx-auto max-w-xl rounded-2xl border border-border bg-card p-8 text-center shadow-sm">
        <h1 className="text-2xl font-bold text-text-primary">
          Create your company
        </h1>
        <p className="mt-3 text-text-secondary">
          Please sign in to set up your company.
        </p>
        <button
          onClick={login}
          className="mt-6 rounded-lg bg-primary px-6 py-2.5 text-sm font-medium text-white transition-colors hover:bg-primary-hover"
        >
          Sign in
        </button>
      </div>
    );
  }

  if (status === "pending" && !companyId) {
    return (
      <div className="mx-auto max-w-xl rounded-2xl border border-border bg-card p-8 text-center shadow-sm">
        <div className="mx-auto flex h-14 w-14 items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-border border-t-primary" />
        </div>
        <h1 className="mt-4 text-2xl font-bold text-text-primary">
          Preparing your onboarding
        </h1>
        <p className="mt-3 text-text-secondary">
          Your company is being set up. This usually takes just a moment — we
          will take you there automatically.
        </p>
      </div>
    );
  }

  if (status === "success") {
    return (
      <div className="mx-auto max-w-xl rounded-2xl border border-border bg-card p-8 text-center shadow-sm">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-primary/10 text-2xl">
          ✓
        </div>
        <h1 className="mt-4 text-2xl font-bold text-text-primary">
          Your company is ready!
        </h1>
        <p className="mt-3 text-text-secondary">
          Your company has been set up successfully. Redirecting to dashboard…
        </p>
        <Link
          href={`/manager/${pollingCompanyId ?? ""}`}
          className="mt-6 inline-block rounded-lg bg-primary px-6 py-2.5 text-sm font-medium text-white transition-colors hover:bg-primary-hover"
        >
          Go to dashboard
        </Link>
      </div>
    );
  }

  if (status === "failed_manual") {
    return (
      <div className="mx-auto max-w-xl rounded-2xl border border-border bg-card p-8 text-center shadow-sm">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-error/10 text-2xl text-error">
          !
        </div>
        <h1 className="mt-4 text-2xl font-bold text-text-primary">
          Something went wrong
        </h1>
        <p className="mt-3 text-text-secondary">
          A problem occurred while creating your company. Our team has been
          notified and is working on a fix.
        </p>
        <Link
          href="/"
          className="mt-6 inline-block rounded-lg bg-primary px-6 py-2.5 text-sm font-medium text-white transition-colors hover:bg-primary-hover"
        >
          Back to dashboard
        </Link>
      </div>
    );
  }

  if(!companyId) {
  return (
    <MotionConfig reducedMotion="user">
      <motion.div
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, ease: EASE }}
        className="mx-auto max-w-xl rounded-2xl border border-border bg-card p-8 shadow-sm"
      >
        <p className="text-xs font-semibold uppercase tracking-wider text-text-secondary">
          Onboarding
        </p>
        <h1 className="mt-2 text-2xl font-bold text-text-primary">
          Create your company
        </h1>
        <p className="mt-2 text-sm text-text-secondary">
          Tell us about your company to get started.
        </p>

        <form onSubmit={(e) => {
          e.preventDefault();
          handleSubmit();
        }} className="mt-8 space-y-5" noValidate>
          <FieldInput
            label="Company name"
            name="name"
            value={values.name}
            onChange={setField("name")}
            error={errors.name}
            required
            placeholder="Acme Logistics"
            autoComplete="organization"
          />

          <FieldInput
            label="Company email"
            name="email"
            type="email"
            value={values.email}
            onChange={setField("email")}
            error={errors.email}
            required
            placeholder="hello@acme.com"
            autoComplete="email"
          />

          <FieldInput
            label="Website"
            name="website"
            type="url"
            value={values.website ?? ""}
            onChange={setField("website")}
            error={errors.website}
            placeholder="https://acme.com"
            autoComplete="url"
          />

          <FieldInput
            label="Location"
            name="location"
            value={values.location ?? ""}
            onChange={setField("location")}
            error={errors.location}
            placeholder="Headquarters address"
          />

          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            <FieldInput
              label="City"
              name="city"
              value={values.city ?? ""}
              onChange={setField("city")}
              error={errors.city}
              placeholder="New York"
              autoComplete="address-level2"
            />
            <FieldInput
              label="Country"
              name="country"
              value={values.country ?? ""}
              onChange={setField("country")}
              error={errors.country}
              placeholder="United States"
              autoComplete="country-name"
            />
          </div>

          {formError && (
            <p
              role="alert"
              className="rounded-lg border border-error/30 bg-error/10 px-4 py-3 text-sm text-error"
            >
              {formError}
            </p>
          )}

          <div className="flex flex-col-reverse items-stretch gap-3 pt-2 sm:flex-row sm:items-center sm:justify-between">
            <Link
              href="/"
              className="rounded-lg px-4 py-2.5 text-center text-sm font-medium text-text-secondary transition-colors hover:bg-gray-100"
            >
              Back
            </Link>

            <motion.button
              type="submit"
              disabled={status === "submitting" || hasErrors}
              whileHover={status !== "submitting" ? { scale: 1.02 } : undefined}
              whileTap={status !== "submitting" ? { scale: 0.97 } : undefined}
              aria-busy={status === "submitting"}
              className="rounded-lg bg-primary px-6 py-2.5 text-sm font-medium text-white transition-colors hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
            >
              {status === "submitting" ? "Creating..." : "Create company"}
            </motion.button>
          </div>
        </form>
      </motion.div>
    </MotionConfig>
  );
}
}
