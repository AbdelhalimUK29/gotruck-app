"use client";

import { memo } from "react";

interface SuperAdminToggleProps {
  isEnabled: boolean;
  isLoading: boolean;
  onChange: (enabled: boolean) => void;
}

export const SuperAdminToggle = memo(function SuperAdminToggle({
  isEnabled,
  isLoading,
  onChange,
}: SuperAdminToggleProps) {
  return (
    <div className="flex items-center gap-4 p-4 rounded-lg border border-border bg-bg-secondary/50">
      <div className="flex-1">
        <h3 className="font-medium text-text-primary">Super Admin</h3>
        <p className="text-sm text-text-secondary mt-1">
          Grant full system access and admin privileges
        </p>
      </div>
      <button
        onClick={() => onChange(!isEnabled)}
        disabled={isLoading}
        className={`relative inline-flex h-8 w-14 items-center rounded-full transition-colors ${
          isEnabled
            ? "bg-primary hover:bg-primary/90"
            : "bg-border hover:bg-border/80"
        } disabled:opacity-50 disabled:cursor-not-allowed`}
        aria-label={isEnabled ? "Disable super admin" : "Enable super admin"}
      >
        <span
          className={`inline-block h-6 w-6 transform rounded-full bg-white transition-transform ${
            isEnabled ? "translate-x-7" : "translate-x-1"
          }`}
        />
      </button>
    </div>
  );
});
