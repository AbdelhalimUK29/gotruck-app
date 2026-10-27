import CompanyForm from "@/components/company-form/CompanyForm";

export const metaMetadata = {
  title: "Create your company | Go Truck",
  description: "Set up your company to get started with Go Truck.",
};

export default function OnboardingPage() {
  return (
    <main className="flex min-h-[70vh] flex-col justify-center bg-bg py-16">
      <CompanyForm />
    </main>
  );
}
