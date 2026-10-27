"use client";

import { memo } from "react";

interface RentalUserCardProps {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  onRemove: (userId: string) => void;
}

export const RentalUserCard = memo(function RentalUserCard({
  id,
  firstName,
  lastName,
  email,
  onRemove,
}: RentalUserCardProps) {
  return (
    <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full border border-border bg-bg-secondary/50 group">
      <div className="h-6 w-6 rounded-full bg-primary/10 flex items-center justify-center flex-shrink-0">
        <span className="text-xs font-semibold text-primary">
          {firstName.charAt(0)}
        </span>
      </div>
      <span className="text-sm font-medium text-text-primary">
        {firstName} {lastName}
      </span>
      <span className="text-xs text-text-secondary hidden sm:block">{email}</span>
      <button
        type="button"
        onClick={() => onRemove(id)}
        className="ml-1 h-5 w-5 rounded-full flex items-center justify-center text-text-secondary hover:text-red-500 hover:bg-red-50 transition-colors opacity-0 group-hover:opacity-100 focus:opacity-100"
        aria-label={`Remove ${firstName} ${lastName}`}
      >
        <svg
          className="h-3 w-3"
          fill="none"
          stroke="currentColor"
          strokeWidth={2}
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M6 18L18 6M6 6l12 12"
          />
        </svg>
      </button>
    </div>
  );
});
