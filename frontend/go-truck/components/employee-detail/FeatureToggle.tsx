"use client";

import { memo } from "react";

interface FeatureToggleProps {
  featureId: string;
  featureName: string;
  isSelected: boolean;
  isLoading: boolean;
  onChange: (featureId: string, selected: boolean) => void;
}

export const FeatureToggle = memo(function FeatureToggle({
  featureId,
  featureName,
  isSelected,
  isLoading,
  onChange,
}: FeatureToggleProps) {
  return (
    <button
      onClick={() => onChange(featureId, !isSelected)}
      disabled={isLoading}
      className={`p-4 rounded-lg border-2 transition-all text-left ${
        isSelected
          ? "border-primary bg-primary/10 hover:bg-primary/20"
          : "border-border bg-bg-secondary hover:bg-border/30"
      } disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-primary/50`}
      type="button"
      aria-pressed={isSelected}
    >
      <div className="flex items-center gap-3">
        <div
          className={`h-5 w-5 rounded border-2 flex items-center justify-center transition-colors ${
            isSelected
              ? "border-primary bg-primary"
              : "border-border bg-transparent"
          }`}
        >
          {isSelected && (
            <svg
              className="h-3 w-3 text-white"
              fill="none"
              stroke="currentColor"
              strokeWidth={3}
              viewBox="0 0 24 24"
            >
              <path d="M5 13l4 4L19 7" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          )}
        </div>
        <span
          className={`font-medium ${
            isSelected ? "text-primary" : "text-text-secondary"
          }`}
        >
          {featureName}
        </span>
      </div>
    </button>
  );
});
