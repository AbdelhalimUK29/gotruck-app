import type { Company, CompanyResponse } from "@/types/company";
import type { CompanyLogoUploadResponse } from "@/types/company-space";

import { api } from "@/src/api/apiClient";

// Feature from GET /manager/companies
export interface CompanyFeature {
  id: string;          // Long id
  featureId: string;   // Long featureId
  featureName: string; // Display name
  createdAt?: Date;    // Instant createdAt
}

export const companyService = {
  create(company: Company, idempotencyKey: string) {
    return api.post<CompanyResponse>("/manager/companies", company, {
      "Idempotency-Key": idempotencyKey,
    });
  },

  update(id: string | number, company: Partial<Company>) {
    return api.put<CompanyResponse>("/manager/companies", {
      id,
      ...company,
    });
  },

  get(id: string | number) {
    return api.get<CompanyResponse>(`/manager/companies/${id}`);
  },

  getProvisioningStatus() {
    return api.get<CompanyResponse>("/manager/companies/provisioning-status");
  },

  uploadLogo(companyId: string | number, file: File) {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("companyId", String(companyId));

    return api.uploadFile<CompanyLogoUploadResponse>(
      "/manager/companies/logo",
      formData,
    );
  },

  getFeatures(): Promise<CompanyFeature[]> {
    return api.get<CompanyFeature[]>("/manager/companies/features");
  },
};
