import CompanySpace from "@/components/company-space/CompanySpace";

export const metadata = {
  title: "Company | Go Truck",
  description: "Manage your company details, users, and rentals",
};

export default async function CompanySpacePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  return <CompanySpace id={id} />;
}