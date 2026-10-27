"use client";

import { useState, useEffect, useRef, useCallback, memo } from "react";
import type { EmployeeResponse, EmployeePage } from "@/types/employee";
import { employeeService } from "@/src/services/employeeService";
import { rentalService } from "@/src/services/rentalService";

interface RentalEmployeeSearchProps {
  rentalId: string;
  excludeUserIds: string[];
  onUserAdded?: () => void;
}

export const RentalEmployeeSearch = memo(function RentalEmployeeSearch({
  rentalId,
  excludeUserIds,
  onUserAdded,
}: RentalEmployeeSearchProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<EmployeeResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Close dropdown on outside click
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // Debounced search
  const searchEmployees = useCallback(async (fullname: string) => {
    if (!fullname.trim()) {
      setResults([]);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data: EmployeePage = await employeeService.getEmployees({
        fullname: fullname.trim(),
        excludeUsers: true,
        page: 0,
        size: 10,
      });
      // Filter out employees already assigned to this rental (defense in depth)
      const filteredResults = data.content.filter(
        (emp) => !excludeUserIds.includes(emp.id)
      );
      setResults(filteredResults);
      setShowDropdown(true);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Search failed");
      setResults([]);
    } finally {
      setLoading(false);
    }
  }, [excludeUserIds]);

  const handleChange = useCallback((value: string) => {
    setQuery(value);
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => searchEmployees(value), 300);
  }, [searchEmployees]);

  const handleAdd = useCallback(async (employeeId: string) => {
    setSubmitting(true);
    setError(null);
    try {
      await rentalService.assignUsersToRental(rentalId, [employeeId]);
      setQuery("");
      setResults([]);
      setShowDropdown(false);
      onUserAdded?.();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to add employee");
    } finally {
      setSubmitting(false);
    }
  }, [rentalId, onUserAdded]);

  return (
    <div ref={dropdownRef} className="relative">
      <div className="flex gap-2">
        <input
          type="text"
          value={query}
          onChange={(e) => handleChange(e.target.value)}
          onFocus={() => results.length > 0 && setShowDropdown(true)}
          placeholder="Search employees…"
          className="flex-1 px-3 py-2 text-sm border border-border rounded-lg bg-bg-secondary focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-transparent transition-all placeholder:text-text-secondary/50"
        />
        <button
          type="button"
          disabled={submitting || !query.trim()}
          onClick={() => { if (results[0]) handleAdd(results[0].id); }}
          className="px-4 py-2 text-sm font-medium text-bg rounded-lg bg-primary hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {submitting ? "Adding…" : "Add"}
        </button>
      </div>

      {error && (
        <p className="mt-1 text-xs text-red-500">{error}</p>
      )}

      {showDropdown && (
        <div className="absolute z-10 mt-1 w-full rounded-lg border border-border bg-bg shadow-lg overflow-hidden">
          {loading ? (
            <div className="px-4 py-3 text-sm text-text-secondary">Searching…</div>
          ) : results.length === 0 && query.trim() ? (
            <div className="px-4 py-3 text-sm text-text-secondary">No employees found</div>
          ) : (
            results.map((emp) => (
              <button
                key={emp.id}
                type="button"
                onClick={() => handleAdd(emp.id)}
                className="w-full flex items-center gap-3 px-4 py-2.5 text-left hover:bg-primary/5 transition-colors border-b border-border/50 last:border-0"
              >
                <div className="h-7 w-7 rounded-full bg-primary/10 flex items-center justify-center flex-shrink-0">
                  <span className="text-xs font-semibold text-primary">
                    {emp.firstName.charAt(0)}
                  </span>
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-medium text-text-primary truncate">
                    {emp.firstName} {emp.lastName}
                  </div>
                  <div className="text-xs text-text-secondary truncate">{emp.email}</div>
                </div>
                <span className="text-xs text-primary font-medium flex-shrink-0">+ Add</span>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
});
