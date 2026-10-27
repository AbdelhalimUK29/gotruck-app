export default function CompanyTab({
  value,
  active,
  label,
  onClick,
}: {
  value: "settings" | "users" | "rentals";
  active: boolean;
  label: string;
  onClick?: () => void;
}) {
  const baseClasses = "flex-1 py-2 px-4 rounded-lg text-sm font-medium transition-colors";
  const activeClasses = "text-black font-semibold";
  const inactiveClasses = "text-text-secondary hover:bg-gray-50";

  return (
    <button
      role="tab"
      aria-selected={active}
      aria-controls={`tab-${value}`}
      onClick={onClick}
      className={`${baseClasses} ${active ? activeClasses : inactiveClasses}`}
    >
      {label}
    </button>
  );
}