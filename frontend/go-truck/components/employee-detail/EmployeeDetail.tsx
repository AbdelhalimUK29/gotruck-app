"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/src/auth/useAuth";
import type {
  EmployeeDetailResponse,
  UpdateEmployeeRequest,
  FeatureRoleEnum,
} from "@/types/employee";
import type { CompanyFeature } from "@/src/services/companyService";
import { employeeService } from "@/src/services/employeeService";
import { companyService } from "@/src/services/companyService";
import { SuperAdminToggle } from "./SuperAdminToggle";
import { FeatureToggle } from "./FeatureToggle";

interface EmployeeDetailProps {
  employeeId: string;
}

// Map feature names to backend enum values
const featureNameToEnum: Record<string, FeatureRoleEnum> = {
  RESERVATION: "RESERVATION",
  CLIENTS: "CLIENTS",
  TRUCKS: "TRUCKS",
  LONG_TIME_RENTAL: "LONG_TIME_RENTAL",
  RENTAL: "RENTAL",
  ADMIN: "ADMIN",
  FINANCIAL_REPORTING: "FINANCIAL_REPORTING",
};

export default function EmployeeDetail({ employeeId }: EmployeeDetailProps) {
  const router = useRouter();
  const { isAuthenticated } = useAuth();
  const [employee, setEmployee] = useState<EmployeeDetailResponse | null>(null);
  const [features, setFeatures] = useState<CompanyFeature[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Local edit state — initialized once from employee data, never reset on refetch
  const [selectedFeatureNames, setSelectedFeatureNames] = useState<Set<string>>(
    new Set()
  );
  const [superAdminEnabled, setSuperAdminEnabled] = useState(false);

  // Track whether we've initialized from employee data
  const initializedRef = useRef(false);

  // Fetch employee details and company features after authentication
  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }

    let isMounted = true;

    const fetchData = async () => {
      try {
        setLoading(true);
        setError(null);

        const [employeeData, featuresData] = await Promise.all([
          employeeService.getEmployeeDetail(employeeId),
          companyService.getFeatures(),
        ]);

        if (!isMounted) return;

        setEmployee(employeeData);
        setFeatures(featuresData);
        // Initialize edit state from fetched data (once per session)
        if (!initializedRef.current && employeeData) {
          initializedRef.current = true;
          setSelectedFeatureNames(
            new Set(employeeData.userFeatures.map((f) => f.featureName) ?? [])
          );
          setSuperAdminEnabled(employeeData.superAdmin);
        }
      } catch (err) {
        if (!isMounted) return;
        const message =
          err instanceof Error ? err.message : "Failed to load employee details";
        setError(message);
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    void fetchData();
    return () => {
      isMounted = false;
    };
  }, [employeeId, isAuthenticated]);
  const handleFeatureToggle = useCallback(
    (featureId: string, selected: boolean) => {
      setSelectedFeatureNames((prev: Set<string>) => {
        const next = new Set(prev);
        const feature = features.find((f) => f.featureId === featureId);
        if (feature) {
          if (selected) {
            next.add(feature.featureName);
          } else {
            next.delete(feature.featureName);
          }
        }
        return next;
      });
    },
    [features]
  );

  const handleSuperAdminToggle = useCallback((enabled: boolean) => {
    setSuperAdminEnabled(enabled);
  }, []);

  const handleSubmit = async () => {
    if (!employee) return;

    try {
      setSubmitting(true);
      setError(null);
      setSuccessMessage(null);

      // Convert feature names to enum values
      const featureEnums: FeatureRoleEnum[] = Array.from(selectedFeatureNames)
        .map((name): FeatureRoleEnum | undefined => featureNameToEnum[name])
        .filter(
          (value): value is FeatureRoleEnum => value !== undefined
        );

      const request: UpdateEmployeeRequest = {
        superAdmin: superAdminEnabled,
        features: featureEnums,
      };

      await employeeService.updateEmployee(employeeId, request);

      setSuccessMessage("Changes saved successfully");

      // Reset message after 3 seconds
      setTimeout(() => {
        setSuccessMessage(null);
      }, 3000);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to save changes";
      setError(message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = () => {
    router.back();
  };

  if (!isAuthenticated || loading || !employee) {
    return (
      <div className="max-w-2xl rounded-xl border border-border bg-bg p-6">
        <div className="space-y-4">
          {/* Header skeleton */}
          <div className="space-y-2">
            <div className="h-6 w-48 bg-border/30 rounded animate-pulse" />
            <div className="h-4 w-64 bg-border/30 rounded animate-pulse" />
          </div>

          {/* SuperAdmin toggle skeleton */}
          <div className="h-20 bg-border/30 rounded-lg animate-pulse" />

          {/* Features section skeleton */}
          <div className="space-y-3">
            <div className="h-5 w-40 bg-border/30 rounded animate-pulse" />
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {[1, 2, 3, 4].map((i) => (
                <div
                  key={i}
                  className="h-16 bg-border/30 rounded-lg animate-pulse"
                />
              ))}
            </div>
          </div>

          {/* Buttons skeleton */}
          <div className="flex gap-2 pt-4">
            <div className="flex-1 h-10 bg-border/30 rounded-lg animate-pulse" />
            <div className="flex-1 h-10 bg-border/30 rounded-lg animate-pulse" />
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="max-w-2xl rounded-xl border border-red-500/50 bg-red-500/5 p-6">
        <div className="flex items-start gap-3">
          <svg
            className="h-5 w-5 text-red-600 flex-shrink-0 mt-0.5"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            viewBox="0 0 24 24"
          >
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

  return (
    <div className="max-w-2xl rounded-xl border border-border bg-bg p-6">
      {/* Success Message */}
      {successMessage && (
        <div className="mb-4 rounded-lg border border-green-500/50 bg-green-500/10 p-4">
          <div className="flex items-center gap-2">
            <svg
              className="h-5 w-5 text-green-600"
              fill="none"
              stroke="currentColor"
              strokeWidth={2}
              viewBox="0 0 24 24"
            >
              <path d="M5 13l4 4L19 7" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            <p className="text-sm font-medium text-green-600">{successMessage}</p>
          </div>
        </div>
      )}

      {/* Error Message */}
      {error && (
        <div className="mb-4 rounded-lg border border-red-500/50 bg-red-500/10 p-4">
          <div className="flex items-start gap-3">
            <svg
              className="h-5 w-5 text-red-600 flex-shrink-0 mt-0.5"
              fill="none"
              stroke="currentColor"
              strokeWidth={2}
              viewBox="0 0 24 24"
            >
              <path d="M12 8v4m0 4v.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <div>
              <p className="text-sm font-medium text-red-600">{error}</p>
            </div>
          </div>
        </div>
      )}

      {/* Employee Header */}
      <div className="mb-6 pb-6 border-b border-border">
        <h2 className="text-xl font-bold text-text-primary">
          {employee.firstName} {employee.lastName}
        </h2>
        <p className="text-sm text-text-secondary mt-1">{employee.email}</p>
      </div>

      {/* Super Admin Section */}
      <div className="mb-6">
        <SuperAdminToggle
          isEnabled={superAdminEnabled}
          isLoading={submitting}
          onChange={handleSuperAdminToggle}
        />
      </div>

      {/* Features Section */}
      <div className="mb-6">
        <h3 className="font-semibold text-text-primary mb-4">
          Assigned Features
        </h3>
        {features.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {features.map((feature) => (
              <FeatureToggle
                key={feature.featureId}
                featureId={feature.featureId}
                featureName={feature.featureName}
                isSelected={selectedFeatureNames.has(feature.featureName)}
                isLoading={submitting}
                onChange={handleFeatureToggle}
              />
            ))}
          </div>
        ) : (
          <div className="rounded-lg border border-border/50 bg-bg-secondary/50 p-6 text-center">
            <p className="text-sm text-text-secondary">
              No features available
            </p>
          </div>
        )}
      </div>

      {/* Action Buttons */}
      <div className="flex gap-3 pt-6 border-t border-border">
        <button
          onClick={handleCancel}
          disabled={submitting}
          className="flex-1 px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-lg hover:bg-bg-secondary transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Cancel
        </button>
        <button
          onClick={handleSubmit}
          disabled={submitting}
          className="flex-1 px-4 py-2 text-sm font-medium text-bg rounded-lg bg-primary hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {submitting ? "Saving..." : "Save Changes"}
        </button>
      </div>
    </div>
  );
}
