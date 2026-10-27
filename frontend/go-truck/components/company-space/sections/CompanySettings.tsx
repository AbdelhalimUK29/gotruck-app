import type { CompanyResponse } from "@/types/company-space";
import CompanySettingsForm from "./CompanySettingsForm";

export default function CompanySettings({ company }: { company: CompanyResponse }) {
  return <CompanySettingsForm company={company} />;
}