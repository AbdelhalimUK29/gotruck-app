import EmployeeDetail from "@/components/employee-detail/EmployeeDetail";

interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function EmployeeDetailPage({ params }: PageProps) {
  const { id } = await params;

  return (
    <div className="flex flex-col gap-6 p-6">
      <div>
        <h1 className="text-2xl font-bold text-text-primary">Edit Employee</h1>
        <p className="text-sm text-text-secondary mt-1">
          Update employee permissions and features
        </p>
      </div>
      <EmployeeDetail employeeId={id} />
    </div>
  );
}
