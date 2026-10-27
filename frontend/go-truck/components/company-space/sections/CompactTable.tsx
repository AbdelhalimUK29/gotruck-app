import type { ReactNode } from "react";

interface CompactTableProps {
  headers: { key: string; label: string }[];
  rows: Record<string, ReactNode>[];
  emptyState?: ReactNode;
  keyExtractor: (row: Record<string, ReactNode>) => string;
}

export default function CompactTable({
  headers,
  rows,
  emptyState,
  keyExtractor,
}: CompactTableProps) {
  return (
    <div className="h-full rounded-xl border border-border overflow-hidden flex flex-col">
      <div className="flex-1">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-xs text-text-secondary uppercase tracking-wider flex-shrink-0">
              {headers.map((header) => (
                <th key={header.key} className="px-3 py-2 text-left">
                  {header.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={keyExtractor(row)} className="border-b border-border/50 hover:bg-gray-50 flex-shrink-0">
                {headers.map((header) => (
                  <td key={header.key} className={`px-3 py-2 text-text-primary flex-1 ${header.key}`}>
                    {row[header.key]}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>

        {emptyState && (
          <div className="h-full">{emptyState}</div>
        )}
      </div>
    </div>
  );
}