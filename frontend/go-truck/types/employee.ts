// Basic employee interface
export interface Employee {
  id?: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  password?: string; // Only for create requests, not returned in responses
}

// Response format from server - matches Page<EmployeeResponse>
export interface EmployeeResponse {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  createdAt?: Date;
  updatedAt?: Date;
}

// Filter parameters for GET /manager/employees
export interface EmployeeFilter {
  fullname?: string; // Search by fullname (firstName + lastName)
  excludeUsers?: boolean; // Exclude users already assigned to a rental
  page: number;       // Page number (default: 0)
  size: number;       // Page size (default: 20, max: 100)
  sort?: string;      // Sort format: "field,asc" or "field,desc"
}

// Request format for creating an employee
export interface CreateEmployeeRequest {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  password: string;
}

// Server response with pagination metadata
export interface EmployeePage {
  content: EmployeeResponse[];
  page: number;
  pageSize: number;
  totalPages: number;
  totalElements: number;
  last: boolean;
  first: boolean;
  size: number;
}

// Feature assigned to an employee
export interface FeatureSummary {
  id: string;           // Feature ID
  featureId: string;    // Long featureId from company
  featureName: string;  // Display name
}

// Detail response for a single employee (with superAdmin status and features)
export interface EmployeeDetailResponse {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  superAdmin: boolean;
  userFeatures: FeatureSummary[];
  createdAt?: Date;
  updatedAt?: Date;
}

// Backend FeatureRoleEnum values
export type FeatureRoleEnum =
  | 'RESERVATION'
  | 'CLIENTS'
  | 'TRUCKS'
  | 'LONG_TIME_RENTAL'
  | 'RENTAL'
  | 'ADMIN'
  | 'FINANCIAL_REPORTING';

// Request to update employee superAdmin status and assigned features
export interface UpdateEmployeeRequest {
  superAdmin: boolean;
  features: FeatureRoleEnum[]; // ALL features the employee should have (complete list, not delta)
}