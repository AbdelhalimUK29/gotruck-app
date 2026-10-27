"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { rentalService } from "@/src/services/rentalService";
import type { RentalResponse } from "@/types/rental";
import { RentalUserCard } from "./RentalUserCard";
import { RentalEmployeeSearch } from "./RentalEmployeeSearch";
import { useAuth } from "@/src/auth/useAuth";

interface RentalDetailProps {
  rentalId: string;
}

type FormState = { name: string; city: string; location: string; email: string };

const validateEmail = (email: string): boolean =>
  /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);

export default function RentalDetail({ rentalId }: RentalDetailProps) {
  const { isLoading: authLoading } = useAuth();
  const [rental, setRental] = useState<RentalResponse | null>(null);
  const [form, setForm] = useState<FormState>({ name: "", city: "", location: "", email: "" });
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const isMountedRef = useRef(true);

  // Extract fetch logic into a reusable function
  const fetchRental = useCallback(() => {
    if (authLoading) return;

    isMountedRef.current = true;
    rentalService
      .getRentalDetail(rentalId)
      .then((data) => {
        if (!isMountedRef.current) return;
        setRental(data);
        setForm({ name: data.name, city: data.city, location: data.location, email: data.email });
      })
      .catch((err: unknown) => {
        if (!isMountedRef.current) return;
        setError(err instanceof Error ? err.message : "Failed to load rental");
      })
      .finally(() => {
        if (isMountedRef.current) setLoading(false);
      });
  }, [rentalId, authLoading]);

  // Wait for auth to initialize before fetching rental data
  useEffect(() => {
    fetchRental();
    return () => {
      isMountedRef.current = false;
    };
  }, [fetchRental]);

  // Callback to refresh rental data after user is added
  const handleUserAdded = useCallback(() => {
    fetchRental();
  }, [fetchRental]);

  const dismissSuccess = useCallback(() => setSuccessMsg(null), []);

  useEffect(() => {
    if (!successMsg) return;
    const t = setTimeout(dismissSuccess, 3000);
    return () => clearTimeout(t);
  }, [successMsg, dismissSuccess]);

  const handleSubmit = useCallback(async () => {
    const trimmed = {
      name: form.name.trim(),
      city: form.city.trim(),
      location: form.location.trim(),
      email: form.email.trim(),
    };

    if (trimmed.city.length > 100) {
      setError("City must not exceed 100 characters");
      return;
    }
    if (trimmed.location.length > 255) {
      setError("Location must not exceed 255 characters");
      return;
    }
    if (trimmed.email && !validateEmail(trimmed.email)) {
      setError("Please enter a valid email address");
      return;
    }
    if (trimmed.email.length > 255) {
      setError("Email must not exceed 255 characters");
      return;
    }

    try {
      setSubmitting(true);
      setError(null);
      setSuccessMsg(null);
      await rentalService.updateRental(rentalId, trimmed);
      setSuccessMsg("Changes saved successfully");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to save changes");
    } finally {
      setSubmitting(false);
    }
  }, [rentalId, form]);

  const handleRemoveUser = useCallback(
    async (userId: string) => {
      setError(null);
      try {
        await rentalService.removeUsersFromRental(rentalId, [userId]);
        setRental((prev) =>
          prev
            ? { ...prev, users: prev.users?.filter((u) => u.id !== userId) }
            : prev
        );
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : "Failed to remove user");
      }
    },
    [rentalId]
  );

  const excludeUserIds = rental?.users?.map((u) => u.id) ?? [];

  if (loading) {
    return (
      <div className="max-w-2xl rounded-xl border border-border bg-bg p-6">
        <div className="space-y-4">
          <div className="h-6 w-48 bg-border/30 rounded animate-pulse" />
          <div className="h-4 w-64 bg-border/30 rounded animate-pulse" />
          <div className="space-y-3 pt-4">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="h-10 bg-border/30 rounded-lg animate-pulse" />
            ))}
          </div>
          <div className="flex justify-end">
            <div className="h-10 w-28 bg-border/30 rounded-lg animate-pulse" />
          </div>
        </div>
      </div>
    );
  }

  if (error && !rental) {
    return (
      <div className="max-w-2xl rounded-xl border border-red-500/50 bg-red-500/5 p-6">
        <div className="flex items-start gap-3">
          <svg className="h-5 w-5 text-red-600 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path d="M12 8v4m0 4v.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <div>
            <h3 className="font-medium text-red-600">Failed to load</h3>
            <p className="text-sm text-red-600/80 mt-1">{error}</p>
            <button
              onClick={() => window.location.reload()}
              className="mt-3 px-3 py-1 text-sm font-medium text-red-600 bg-red-600/10 hover:bg-red-600/20 rounded transition-colors"
            >
              Retry
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (!rental) return null;

  return (
    <div className="max-w-2xl rounded-xl border border-border bg-bg p-6">
      {successMsg && (
        <div className="mb-4 rounded-lg border border-green-500/50 bg-green-500/10 p-4">
          <div className="flex items-center gap-2">
            <svg className="h-5 w-5 text-green-600" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
              <path d="M5 13l4 4L19 7" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            <p className="text-sm font-medium text-green-600">{successMsg}</p>
            <button onClick={dismissSuccess} className="ml-auto text-green-600 hover:text-green-700">
              <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      )}

      {error && (
        <div className="mb-4 rounded-lg border border-red-500/50 bg-red-500/10 p-4">
          <p className="text-sm font-medium text-red-600">{error}</p>
        </div>
      )}

      {/* Rental Info Form */}
      <div className="mb-8">
        <h2 className="text-lg font-bold text-text-primary mb-1">Rental Details</h2>
        <p className="text-sm text-text-secondary mb-5">Update the rental location information</p>

        <div className="space-y-3">
          <div>
            <label htmlFor="rental-name" className="block text-sm font-medium text-text-primary mb-1">Name</label>
            <input
              id="rental-name"
              type="text"
              value={form.name}
              onChange={(e) => setForm((p) => ({ ...p, name: e.target.value.slice(0, 255) }))}
              placeholder="Enter rental name"
              maxLength={255}
              className="w-full px-3 py-2 text-sm border border-border rounded-lg bg-bg-secondary focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-transparent transition-all placeholder:text-text-secondary/50"
            />
          </div>

          <div>
            <label htmlFor="rental-city" className="block text-sm font-medium text-text-primary mb-1">City</label>
            <input
              id="rental-city"
              type="text"
              value={form.city}
              onChange={(e) => setForm((p) => ({ ...p, city: e.target.value.slice(0, 100) }))}
              placeholder="Enter city"
              maxLength={100}
              className="w-full px-3 py-2 text-sm border border-border rounded-lg bg-bg-secondary focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-transparent transition-all placeholder:text-text-secondary/50"
            />
          </div>

          <div>
            <label htmlFor="rental-location" className="block text-sm font-medium text-text-primary mb-1">Location</label>
            <input
              id="rental-location"
              type="text"
              value={form.location}
              onChange={(e) => setForm((p) => ({ ...p, location: e.target.value.slice(0, 255) }))}
              placeholder="Enter location"
              maxLength={255}
              className="w-full px-3 py-2 text-sm border border-border rounded-lg bg-bg-secondary focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-transparent transition-all placeholder:text-text-secondary/50"
            />
          </div>

          <div>
            <label htmlFor="rental-email" className="block text-sm font-medium text-text-primary mb-1">Email</label>
            <input
              id="rental-email"
              type="email"
              value={form.email}
              onChange={(e) => setForm((p) => ({ ...p, email: e.target.value.slice(0, 255) }))}
              placeholder="rental@company.com"
              maxLength={255}
              className="w-full px-3 py-2 text-sm border border-border rounded-lg bg-bg-secondary focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-transparent transition-all placeholder:text-text-secondary/50"
            />
          </div>
        </div>

        <div className="mt-5 flex justify-end">
          <button
            onClick={handleSubmit}
            disabled={submitting}
            className="px-5 py-2 text-sm font-medium text-bg rounded-lg bg-primary hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {submitting ? "Saving…" : "Save Changes"}
          </button>
        </div>
      </div>

      {/* Divider */}
      <div className="border-t border-border my-6" />

      {/* Assigned Users */}
      <div>
        <h2 className="text-lg font-bold text-text-primary mb-1">Assigned Users</h2>
        <p className="text-sm text-text-secondary mb-4">Employees who have access to this rental</p>

        {(!rental.users || rental.users.length === 0) ? (
          <div className="rounded-lg border border-dashed border-border p-6 text-center mb-4">
            <p className="text-sm text-text-secondary">No users assigned yet</p>
          </div>
        ) : (
          <div className="flex flex-wrap gap-2 mb-4">
            {rental.users.map((user) => (
              <RentalUserCard
                key={user.id}
                id={user.id}
                firstName={user.firstName}
                lastName={user.lastName}
                email={user.email}
                onRemove={handleRemoveUser}
              />
            ))}
          </div>
        )}

        <RentalEmployeeSearch
          rentalId={rentalId}
          excludeUserIds={excludeUserIds}
          onUserAdded={handleUserAdded}
        />
      </div>
    </div>
  );
}
