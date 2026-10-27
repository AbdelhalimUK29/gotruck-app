export interface Company {
  id?: string;
  name: string;
  website?: string;
  email: string;
  location?: string;
  city?: string;
  country?: string;
  logo?: string;
  provisioningStatus?: string;
}

export interface CompanyResponse {
  id: string;
  name: string;
  website?: string;
  email: string;
  location?: string;
  city?: string;
  country?: string;
  logo?: string;
  provisioningStatus?: string;
}
