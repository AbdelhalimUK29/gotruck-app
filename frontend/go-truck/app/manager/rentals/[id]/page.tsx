import RentalDetail from "@/components/rental-detail/RentalDetail";

interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function RentalDetailPage({ params }: PageProps) {
  const { id } = await params;

  return (
    <div className="flex flex-col gap-6 p-6">
      <div>
        <h1 className="text-2xl font-bold text-text-primary">Edit Rental</h1>
        <p className="text-sm text-text-secondary mt-1">
          Update rental details and manage assigned users
        </p>
      </div>
      <RentalDetail rentalId={id} />
    </div>
  );
}
