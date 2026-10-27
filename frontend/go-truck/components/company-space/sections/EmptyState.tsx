import type { ReactNode } from "react";

export default function EmptyState({
  icon: Icon,
  title,
  description,
  buttonLabel,
}: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  description: string;
  buttonLabel: string;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 p-12">
      <Icon className="h-12 w-12 text-gray-300" />
      <p className="text-sm text-text-secondary">{description}</p>
      <button className="text-sm text-primary font-medium whitespace-nowrap">
        {buttonLabel}
      </button>
    </div>
  );
}