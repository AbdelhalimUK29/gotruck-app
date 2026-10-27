export interface CompanyResponse {
  id: string;
  name: string;
  email: string;
  website?: string;
  location?: string;
  city?: string;
  country?: string;
  logo?: string;
  provisioningStatus?: string;
}

export interface CompanyLogoUploadResponse {
  success: boolean;
  message: string;
  logoUrl?: string;
}

export interface User {
  id: string;
  email: string;
  role: "Admin" | "Manager" | "Driver";
}

export interface Rental {
  id: string;
  plate: string;
  status: "Active" | "Inactive" | "Maintenance";
  lastService?: Date;
}