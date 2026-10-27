"use client";

import { useState, useEffect, useCallback, memo, type JSX } from "react";
import { useRouter } from "next/navigation";
import type { EmployeeResponse, EmployeeFilter, CreateEmployeeRequest } from "@/types/employee";
import { employeeService } from "@/src/services/employeeService";

type PaginationClickHandler = (action: "first" | "prev" | "next" | "last") => void;

interface EmployeeRowProps extends EmployeeResponse {
  onClick: () => void;
}

const EmployeeRow = memo(function EmployeeRow({ email, firstName, lastName, onClick }: EmployeeRowProps) {
  const fullname = `${firstName} ${lastName}`;
  const initial = email.charAt(0).toUpperCase();

  return (
    <div
      className="flex items-center gap-3 px-4 py-3 border-b border-border/50 hover:bg-primary/5 transition-colors cursor-pointer group"
      onClick={onClick}
    >
      <div className="h-10 w-10 rounded-full bg-primary/10 flex items-center justify-center flex-shrink-0">
        <span className="text-sm font-semibold text-primary">{initial}</span>
      </div>

      <div className="flex-1 min-w-0">
        <div className="font-medium text-text-primary truncate">{fullname}</div>
        <div className="text-xs text-text-secondary truncate">{email}</div>
      </div>

      <svg
        className="h-4 w-4 text-text-secondary opacity-0 group-hover:opacity-100 transition-opacity"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        viewBox="0 0 24 24"
      >
        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
      </svg>
    </div>
  );
});

interface PaginationControlsProps {
  page: number;
  totalPages: number;
  onClick: PaginationClickHandler;
}

const PaginationControls = memo(function PaginationControls({ page, totalPages, onClick }: PaginationControlsProps) {
  const canGoFirst = page > 0;
  const canGoPrev = page > 0;
  const canGoNext = page < totalPages - 1;
  const canGoLast = page < totalPages - 1;

  return (
    <div className="flex items-center justify-between px-4 py-2 border-b border-border bg-bg-secondary/50">
      <div className="flex items-center gap-1">
        <button
          className="px-2 py-1 text-sm text-text-secondary hover:text-primary hover:bg-primary/5 rounded disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          onClick={() => onClick("first")}
          disabled={!canGoFirst}
          aria-label="First page"
        >
          &laquo;
        </button>
        <button
          className="px-2 py-1 text-sm text-text-secondary hover:text-primary hover:bg-primary/5 rounded disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          onClick={() => onClick("prev")}
          disabled={!canGoPrev}
          aria-label="Previous page"
        >
          &lsaquo;
        </button>
        <span className="text-sm text-text-secondary px-2">
          Page {page + 1} of {totalPages}
        </span>
        <button
          className="px-2 py-1 text-sm text-text-secondary hover:text-primary hover:bg-primary/5 rounded disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          onClick={() => onClick("next")}
          disabled={!canGoNext}
          aria-label="Next page"
        >
          &rsaquo;
        </button>
        <button
          className="px-2 py-1 text-sm text-text-secondary hover:text-primary hover:bg-primary/5 rounded disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          onClick={() => onClick("last")}
          disabled={!canGoLast}
          aria-label="Last page"
        >
          &raquo;
        </button>
      </div>
    </div>
  );
});

interface CreateEmployeeDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

function CreateEmployeeDialog({ isOpen, onClose, onSuccess }: CreateEmployeeDialogProps) {
  const [formData, setFormData] = useState<CreateEmployeeRequest>({
    username: "",
    email: "",
    firstName: "",
    lastName: "",
    password: "",
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const validateEmail = (email: string): boolean => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  };

  const handleSubmit = async (e: React.ChangeEvent<HTMLFormElement> & { preventDefault(): void }) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      if (!formData.username.trim()) {
        throw new Error("Username is required");
      }

      if (!validateEmail(formData.email)) {
        throw new Error("Please enter a valid email address");
      }

      if (formData.password.length < 8) {
        throw new Error("Password must be at least 8 characters");
      }

      await employeeService.createEmployee(formData);

      setFormData({
        username: "",
        email: "",
        firstName: "",
        lastName: "",
        password: "",
      });
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to create employee";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
      <div className="w-full max-w-md rounded-xl border border-border bg-bg p-6 shadow-lg animate-in fade-in zoom-in-95">
        <div className="mb-4">
          <h2 className="text-lg font-bold text-text-primary">Add Employee</h2>
          <p className="text-sm text-text-secondary mt-1">Create a new company employee</p>
        </div>

        {error && (
          <div className="mb-4 rounded-lg border border-red-500/50 bg-red-500/10 p-3">
            <p className="text-sm text-red-600">{error}</p>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label htmlFor="username" className="block text-sm font-medium text-text-primary mb-1">
              Username <span className="text-red-500">*</span>
            </label>
            <input
              id="username"
              type="text"
              value={formData.username}
              onChange={(e) => setFormData(prev => ({ ...prev, username: e.target.value }))}
              placeholder="Enter username"
              className="w-full px-3 py-2 text-sm border border-border rounded-lg bg-bg-secondary focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-transparent transition-all"
              disabled={loading}
              required
            />
          </div>

          <div>
            <label htmlFor="email" className="block text-sm font-medium text-text-primary mb-1">
              Email <span className="text-red-500">*</span>
            </label>
            <input
              id="email"
              type="email"
              value={formData.email}
              onChange={(e) => setFormData(prev => ({ ...prev, email: e.target.value }))}
              placeholder="employee@company.com"
              className="w-full px-3 py-2 text-sm border border-border rounded-lg bg-bg-secondary focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-transparent transition-all"
              disabled={loading}
              required
            />
          </div>

          <div>
            <label htmlFor="firstName" className="block text-sm font-medium text-text-primary mb-1">
              First Name <span className="text-red-500">*</span>
            </label>
            <input
              id="firstName"
              type="text"
              value={formData.firstName}
              onChange={(e) => setFormData(prev => ({ ...prev, firstName: e.target.value }))}
              placeholder="Enter first name"
              className="w-full px-3 py-2 text-sm border border-border rounded-lg bg-bg-secondary focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-transparent transition-all"
              disabled={loading}
              required
            />
          </div>

          <div>
            <label htmlFor="lastName" className="block text-sm font-medium text-text-primary mb-1">
              Last Name <span className="text-red-500">*</span>
            </label>
            <input
              id="lastName"
              type="text"
              value={formData.lastName}
              onChange={(e) => setFormData(prev => ({ ...prev, lastName: e.target.value }))}
              placeholder="Enter last name"
              className="w-full px-3 py-2 text-sm border border-border rounded-lg bg-bg-secondary focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-transparent transition-all"
              disabled={loading}
              required
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-text-primary mb-1">
              Password <span className="text-red-500">*</span>
            </label>
            <input
              id="password"
              type="password"
              value={formData.password}
              onChange={(e) => setFormData(prev => ({ ...prev, password: e.target.value }))}
              placeholder="••••••••"
              className="w-full px-3 py-2 text-sm border border-border rounded-lg bg-bg-secondary focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-transparent transition-all"
              disabled={loading}
              required
              minLength={8}
            />
            <p className="mt-1 text-xs text-text-secondary/70">Must be at least 8 characters</p>
          </div>

          <div className="flex gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="flex-1 px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-lg hover:bg-bg-secondary transition-colors disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              className="flex-1 px-4 py-2 text-sm font-medium text-bg rounded-lg bg-primary hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? "Creating..." : "Create"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function EmployeeList(): JSX.Element {
  const router = useRouter();
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [filter, setFilter] = useState<EmployeeFilter>({
    page: 0,
    size: 20,
    fullname: "",
    sort: undefined,
  });
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);

  const [searchInput, setSearchInput] = useState("");

  useEffect(() => {
    let isMounted = true;

    const fetchEmployees = async () => {
      try {
        if (!isMounted) return;
        setLoading(true);
        setError(null);

        const result = await employeeService.getEmployees(filter);
        if (!isMounted) return;
        setEmployees(result.content);
        setTotalPages(result.totalPages);
      } catch (err: unknown) {
        if (!isMounted) return;
        const message =
          err instanceof Error ? err.message : "Failed to load employees";
        setError(message);
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    void fetchEmployees();
    return () => {
      isMounted = false;
    };
  }, [filter]);

  const handleSearch = useCallback(() => {
    setFilter(prev => ({ ...prev, fullname: searchInput, page: 0 }));
  }, [searchInput]);
  
  const loadEmployees = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await employeeService.getEmployees(filter);
      setEmployees(result.content);
      setTotalPages(result.totalPages);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to load employees";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [filter]);

  const handlePageChange = useCallback(
    (action: "first" | "prev" | "next" | "last") => {
      switch (action) {
        case "first":
          setFilter(prev => ({ ...prev, page: 0 }));
          break;
        case "prev":
          setFilter(prev => ({ ...prev, page: Math.max(0, prev.page - 1) }));
          break;
        case "next":
          setFilter(prev => ({ ...prev, page: prev.page + 1 }));
          break;
        case "last":
          setFilter(prev => ({ ...prev, page: Math.max(0, totalPages - 1) }));
          break;
      }
    },
    [totalPages],
  );

  const handleEmployeeClick = useCallback(
    (id: string | undefined) => {
      if (id) {
        router.push(`/manager/employees/${id}`);
      }
    },
    [router],
  );

  if (loading) {
    return (
      <div className="w-full rounded-xl border border-border flex flex-col">
        <div className="border-b border-border p-3">
          <div className="flex items-center justify-between">
            <div className="h-4 w-16 bg-border rounded animate-pulse" />
            <div className="h-4 w-12 bg-border rounded animate-pulse" />
          </div>
        </div>
        <div className="flex-1">
          {[1, 2, 3].map(i => (
            <div
              key={i}
              className="flex items-center gap-3 px-4 py-3 border-b border-border/50"
            >
              <div className="h-10 w-10 bg-border/30 rounded-full animate-pulse" />
              <div className="flex-1">
                <div className="h-4 w-32 bg-border/30 rounded animate-pulse mb-2" />
                <div className="h-3 w-24 bg-border/30 rounded animate-pulse" />
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="w-full rounded-xl border border-red-500/50 flex flex-col bg-red-500/5">
        <div className="border-b border-red-500/30 p-3">
          <span className="text-sm font-medium text-red-600">
            {error}
          </span>
        </div>
        <div className="flex-1 flex flex-col items-center justify-center p-8">
          <p className="text-sm text-red-600/80 mb-4">
            Unable to load employees
          </p>
          <button
            onClick={() => loadEmployees().catch(() => {
              // Error already handled in loadEmployees
            })}
            className="px-4 py-2 text-sm font-medium text-red-600 bg-red-600/10 hover:bg-red-600/20 rounded-lg transition-colors"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  const hasEmployees = employees.length > 0;

  return (
    <>
      <div className="w-full max-w-lg rounded-xl border border-border flex flex-col bg-bg">
        <PaginationControls
          page={filter.page}
          totalPages={totalPages}
          onClick={handlePageChange}
        />

        <div className="border-b border-border p-3 flex gap-2">
          <input
            type="text"
            placeholder="Search by fullname..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                handleSearch();
              }
            }}
            className="flex-1 px-3 py-2 text-sm border border-border rounded-lg bg-bg-secondary focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-transparent transition-all placeholder:text-text-secondary/50"
          />
          <button
            onClick={handleSearch}
            className="px-4 py-2 text-sm font-medium text-bg rounded-lg bg-primary hover:bg-primary/90 transition-colors"
          >
            Search
          </button>
        </div>

        <div className="flex-1 overflow-y-auto max-h-[500px]">
          {hasEmployees ? (
            employees.map((employee) => (
              <EmployeeRow
                key={employee.id}
                {...employee}
                onClick={() => handleEmployeeClick(employee.id)}
              />
            ))
          ) : (
            <div className="flex flex-col items-center justify-center p-16">
              <svg
                className="h-16 w-16 text-text-secondary/30"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.5}
                  d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"
                />
              </svg>
              <p className="mt-4 text-sm text-text-secondary">No employees found</p>
              {filter.fullname && (
                <p className="mt-1 text-xs text-text-secondary/70">
                  Try adjusting your search
                </p>
              )}
            </div>
          )}
        </div>

        <div className="border-t border-border p-4">
          <button
            onClick={() => setIsCreateDialogOpen(true)}
            className="w-full flex items-center justify-center gap-2 px-4 py-3 text-sm font-medium text-bg rounded-lg bg-primary hover:bg-primary/90 transition-colors shadow-lg shadow-primary/20"
          >
            <svg
              className="h-5 w-5"
              fill="none"
              stroke="currentColor"
              strokeWidth={2}
              strokeLinecap="round"
              strokeLinejoin="round"
              viewBox="0 0 24 24"
            >
              <path d="M12 4v16m8-8H4" />
            </svg>
            Add Employee
          </button>
        </div>
      </div>

      <CreateEmployeeDialog
        isOpen={isCreateDialogOpen}
        onClose={() => setIsCreateDialogOpen(false)}
        onSuccess={() => {
          setFilter(prev => ({ ...prev, page: 0 }));
          loadEmployees().catch(() => {
            // Error already handled in loadEmployees
          });
        }}
      />
    </>
  );
}
