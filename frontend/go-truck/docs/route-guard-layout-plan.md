# RouteGuard Layout Refactoring Plan

## Problem
Currently each `/manager` route is manually wrapped with `RouteGuard`, requiring `require companyId={false}` prop just for the onboarding page.

## Goal
Create a single `app/manager/layout.tsx` that wraps all `/manager` routes with `RouteGuard`, eliminating the need for per-route guard wrappers.

## Files to Modify

### 1. Create `app/manager/layout.tsx`
```tsx
import { RouteGuard } from "@/src/auth/routeGuard";

export default function ManagerLayout({ children }: { children: React.ReactNode }) {
  return (
    <RouteGuard requireCompanyId={true}>
      {children}
    </RouteGuard>
  );
}
```

### 2. Add constant for onboarding path
Create `src/auth/routeGuardConstants.ts` (or define in `routeGuard.tsx`):
```tsx
export const ONBOARDING_PATH = "/manager/onboarding";
```

### 3. Update `src/auth/routeGuard.tsx`
**Changes:**
- Add `ONBOARDING_PATH` constant
- Pre-check pathname to skip companyId check for onboarding
- Simplify conditional logic

```tsx
// At top of group, add:
import { usePathname } from "next/navigation";
const ONBOARDING_PATH = "/manager/onboarding";

export function RouteGuard({ children, requireCompanyId = true }: RouteGuardProps) {
  const { isLoading, isAuthenticated, companyId } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (isLoading) return;

    // Not authenticated → redirect
    if (!isAuthenticated) {
      return;
    }

    // Has companyId but onboarding → redirect to dashboard
    if (requireCompanyId && companyId && pathname === ONBOARDING_PATH) {
      router.push(`/manager/${companyId}`);
      return;
    }

    // Require companyId but missing and not onboarding → redirect
    if (requireCompanyId && !companyId && pathname !== ONBOARDING_PATH) {
      router.push("/manager/onboarding");
      return;
    }
  }, [isLoading, isAuthenticated, companyId, pathname, requireCompanyId]);

  // Show loading state
  if (isLoading) {
    return <LoadingSpinner />;
  }

  // Render-based gates (avoid parallel checks)
  return <>{children}</>;
}
```

### 4. Update `app/manager/onboarding/page.tsx`
Remove `RouteGuard` wrapper, just use `RouteGuard` via the layout:

```tsx
export default function OnboardingPage() {
  return (
    <main className="flex min-h-[70vh] flex-col justify-center bg-bg py-16">
      <CompanyForm />
    </main>
  );
}
```

The layout provides `RouteGuard` → onboarding is exempt by `pathname !== ONBOARDING_PATH` check.

### 5. Update other manager pages
Remove `RouteGuard` wrappers from:
- `app/manager/[id]/page.tsx`
- `app/manager/employees/[id]/page.tsx`
- `app/manager/rentals/[id]/page.tsx`

### 6. Update other non-manager routes (if any)
Remove `RouteGuard` wrappers from:
- `app/manager/create-company/page.tsx` - if still exists (renamed to onboarding)
- Any other authenticated routes

## Changes Summary

| File | Action |
|------|--------|
| `app/manager/layout.tsx` | Create new layout file |
| `src/auth/routeGuard.tsx` | Add pathname-based conditional logic |
| `src/auth/routeGuardConstants.ts` | Create (optional, or inline) |
| `app/manager/onboarding/page.tsx` | Remove RouteGuard wrapper |
| `app/manager/[id]/page.tsx` | Remove RouteGuard wrapper |
| `app/manager/employees/[id]/page.tsx` | Remove RouteGuard wrapper |
| `app/manager/rentals/[id]/page.tsx` | Remove RouteGuard wrapper |

## Benefits

1. **Reduced declarative noise**: Single `<RouteGuard requireCompanyId={true}>` in layout instead of 5+ individual wrappers
2. **Consistent behavior**: All manager routes inherit protection automatically
3. **Less boilerplate**: No need to pass `requireCompanyId` to every page
4. **Easier maintenance**: Authentication logic lives in one place (layout + routeGuard), not scattered across templates

## Migration Steps

1. Create `app/manager/layout.tsx`
2. Add pathname-based logic to `routeGuard.tsx` (double-check test that onboarding requires companyId=false)
3. Remove `RouteGuard` from all `/manager/*` pages
4. Remove `RouteGuard` from any other routes you wrap (e.g., `/auth/*` if any)
5. Verify onboarding still works: redirect to company dashboard if user has companyId
6. Verify other manager routes still work: block users without companyId, redirect to onboarding
7. Verify other routes are protected: reject unauthenticated users

## Testing Checklist

- [ ] Guest user visits `/manager/onboarding` → sees onboarding page
- [ ] Guest user visits `/manager/(valid-id)` → redirects to keycloak login or show custom auth prompt
- [ ] User without companyId visits `/manager/[id]` → redirects to `/manager/onboarding`
- [ ] User with companyId visits `/manager/onboarding` → redirects to `/manager/${companyId}`
- [ ] User with companyId visits `/manager/[valid-id]` → sees page content
- [ ] All manager pages protected by layout