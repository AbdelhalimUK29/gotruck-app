export default function CompanyLabel({ name }: { name: string }) {
  return (
    <div className="flex items-center gap-2 whitespace-nowrap">
      <span className="text-sm font-medium text-text-secondary">
        {name}
      </span>
    </div>
  );
}