// User assigned to a rental
export interface RentalUserResponse {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
}

// Rental response from backend
export interface RentalResponse {
  id: string;
  companyId: string;
  name: string;
  city: string;
  location: string;
  email: string;
  logo?: string;
  deleted: boolean;
  users?: RentalUserResponse[];
  createdAt: string; // ISO 8601 date string from JSON
  updatedAt: string; // ISO 8601 date string from JSON
}

// Filter parameters for GET /manager/rentals
export interface RentalFilter {
  name?: string; // Search by rental name
  page: number; // Page number (default: 0)
  size: number; // Page size (default: 20, max: 100)
  sort?: string; // Sort format: "field,asc" or "field,desc"
}

// Request format for creating a rental
export interface CreateRentalRequest {
  name: string;
  city: string;
  location: string;
  email: string;
}

// Request format for updating a rental
export interface UpdateRentalRequest {
  name: string;
  city: string;
  location: string;
  email: string;
  logo?: string;
}

// Server response with pagination metadata
export interface RentalPage {
  content: RentalResponse[];
  page: number;
  pageSize: number;
  totalPages: number;
  totalElements: number;
  last: boolean;
  first: boolean;
  size: number;
}

// Detail response for a single rental (same shape as RentalResponse)
export type RentalDetailResponse = RentalResponse;
