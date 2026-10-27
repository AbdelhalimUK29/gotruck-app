import { RouteGuard } from "@/src/auth/routeGuard";

export default function ManagerLayout({ children }: { children: React.ReactNode }) {
  return (
    <RouteGuard requireCompanyId={true}>
      {children}
    </RouteGuard>
  );
}