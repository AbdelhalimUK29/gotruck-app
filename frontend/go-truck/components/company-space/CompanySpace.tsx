"use client";

import { useState, useEffect } from "react";
import CompanyTab from "./CompanyTab";
import CompanySettings from "./sections/CompanySettings";
import { EmployeeList } from "@/components/employee-list";
import RentalList from "./sections/RentalList";
import type { CompanyResponse } from "@/types/company-space";
import { companyService } from "@/src/services/companyService";
import { useAuth } from "@/src/auth/useAuth";
import { usePermission } from "@/src/hooks/usePermission";

type TabValue = "settings" | "users" | "rentals";

export default function CompanySpace({ id }: { id: string }) {
  const [company, setCompany] = useState<CompanyResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabValue>("settings");

  const { isLoading, isAuthenticated } = useAuth();
  const {
    canAccessSettings,
    canAccessEmployees,
    canAccessRentals,
  } = usePermission();

  // Derive active tab from permissions (no useEffect needed)
  // If user cannot access current tab, switch to an accessible one
  const getActiveTab = (): TabValue => {
    if (tab === "settings" && !canAccessSettings) {
      return canAccessEmployees ? "users" : (canAccessRentals ? "rentals" : "settings");
    }
    if (tab === "users" && !canAccessEmployees) {
      return canAccessSettings ? "settings" : (canAccessRentals ? "rentals" : "users");
    }
    if (tab === "rentals" && !canAccessRentals) {
      return canAccessSettings ? "settings" : (canAccessEmployees ? "users" : "rentals");
    }
    return tab;
  };

  const activeTab = getActiveTab();

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }
    companyService
      .get(id)
      .then(setCompany)
      .catch((err: unknown) =>
        setError(err instanceof Error ? err.message : "Failed to load company"),
      );
  }, [id, isAuthenticated]);

  if (isLoading || !isAuthenticated || !company) {
    return (
      <div className="w-full h-screen bg-bg">
        <CompanySpaceSkeleton />
      </div>
    );
  }

  if (error) {
    return (
      <div className="w-full h-screen flex items-center justify-center bg-bg">
        <div className="text-red-500">
          Error: {error || "Failed to load company"}
        </div>
      </div>
    );
  }

  return (
    <div className="w-full flex flex-col bg-bg">
      {/* Header with Company Name */}
      <header className="h-14 border-b border-border bg-bg">
        <div className="flex items-center gap-6 h-full px-6">
          <div className="flex items-center gap-3">
            <svg
              className="w-6 h-6 text-primary"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              viewBox="0 0 24 24"
            >
              <path d="M3 21h18M3 10h18M5 6h14M7 3h10v4H7z" />
              <path d="M7 10v11M12 10v11M17 10v11" />
            </svg>
            <span className="text-xl font-bold text-text-primary tracking-tight uppercase">
              {company.name}
            </span>
          </div>
        </div>
      </header>

      {/* Tabs */}
      <div className="gap-2 px-4 pt-12 pb-3 border-b border-border">
        <div className="flex items-center gap-1">
          {canAccessSettings && (
            <CompanyTab
              value="settings"
              active={activeTab === "settings"}
              label="Company Settings"
              onClick={() => setTab("settings")}
            />
          )}
          {canAccessEmployees && (
            <CompanyTab
              value="users"
              active={activeTab === "users"}
              label="Manage Users"
              onClick={() => setTab("users")}
            />
          )}
          {canAccessRentals && (
            <CompanyTab
              value="rentals"
              active={activeTab === "rentals"}
              label="Manage Rentals"
              onClick={() => setTab("rentals")}
            />
          )}
        </div>
      </div>

      {/* Content - Full Width, Natural Height */}
      <div className="w-full flex-1 px-4 py-4">
        {activeTab === "settings" && canAccessSettings && <CompanySettings company={company} />}
        {activeTab === "users" && canAccessEmployees && <EmployeeList />}
        {activeTab === "rentals" && canAccessRentals && <RentalList />}
      </div>
    </div>
  );
}

// Skeleton loader for CompanySpace
function CompanySpaceSkeleton() {
  return (
    <div className="w-full h-screen flex flex-col bg-bg">
      {/* Header Skeleton */}
      <header className="h-14 border-b border-border bg-bg">
        <div className="flex items-center gap-6 h-full px-6">
          <div className="flex items-center gap-3">
            <div className="h-6 w-6 bg-border/30 rounded animate-pulse" />
            <div className="h-6 w-32 bg-border/30 rounded animate-pulse" />
          </div>
        </div>
      </header>

      {/* Tabs Skeleton */}
      <div className="gap-2 px-4 pt-12 pb-3 border-b border-border">
        <div className="flex items-center gap-1">
          <div className="h-8 w-28 bg-border/30 rounded animate-pulse" />
          <div className="h-8 w-28 bg-border/30 rounded animate-pulse" />
          <div className="h-8 w-28 bg-border/30 rounded animate-pulse" />
        </div>
      </div>

      {/* Content Skeleton */}
      <div className="w-full flex-1 px-4 py-4">
        <div className="space-y-4">
          {/* Card 1 */}
          <div className="rounded-xl border border-border bg-bg p-6">
            <div className="h-6 w-48 bg-border/30 rounded animate-pulse mb-4" />
            <div className="space-y-3">
              <div className="h-10 w-full bg-border/30 rounded animate-pulse" />
              <div className="h-10 w-full bg-border/30 rounded animate-pulse" />
              <div className="h-10 w-full bg-border/30 rounded animate-pulse" />
              <div className="h-10 w-full bg-border/30 rounded animate-pulse" />
            </div>
          </div>

          {/* Card 2 */}
          <div className="rounded-xl border border-border bg-bg p-6">
            <div className="h-6 w-48 bg-border/30 rounded animate-pulse mb-4" />
            <div className="space-y-3">
              <div className="h-10 w-full bg-border/30 rounded animate-pulse" />
              <div className="h-10 w-full bg-border/30 rounded animate-pulse" />
              <div className="h-10 w-full bg-border/30 rounded animate-pulse" />
            </div>
          </div>

          {/* Card 3 */}
          <div className="rounded-xl border border-border bg-bg p-6">
            <div className="h-6 w-48 bg-border/30 rounded animate-pulse mb-4" />
            <div className="space-y-3">
              <div className="h-10 w-full bg-border/30 rounded animate-pulse" />
              <div className="h-10 w-full bg-border/30 rounded animate-pulse" />
              <div className="h-10 w-full bg-border/30 rounded animate-pulse" />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}