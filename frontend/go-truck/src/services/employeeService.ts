import { api } from "@/src/api/apiClient";
import type {
  EmployeeResponse,
  EmployeeFilter,
  CreateEmployeeRequest,
  EmployeePage,
  EmployeeDetailResponse,
  UpdateEmployeeRequest,
} from "@/types/employee";

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 20;

/**
 * Get paginated list of employees for a company with optional filtering
 */
export const employeeService = {
  /**
   * Fetch employees with pagination and filtering
   * @param filter - Filter parameters (fullname search, pagination, sorting)
   * @returns Promise resolving to paginated employee data
   */
  getEmployees(filter: EmployeeFilter): Promise<EmployeePage> {
    // Build query string from filter
    const queryParams = new URLSearchParams();
    queryParams.append("page", String(filter.page ?? 0));
    queryParams.append("size", String(filter.size ?? 20));
    if (filter.fullname) queryParams.append("fullname", filter.fullname);
    if (filter.excludeUsers) queryParams.append("excludeUsers", "true");
    if (filter.sort) queryParams.append("sort", filter.sort);

    const query = queryParams.toString();
    const endpoint = `/manager/employees${query ? `?${query}` : ""}`;

    return api.get<EmployeePage>(endpoint);
  },

  /**
   * Create a new employee
   * @param request - Employee creation request data
   * @returns Promise resolving to created employee data
   */
  createEmployee(request: CreateEmployeeRequest): Promise<EmployeeResponse> {
    return api.post<EmployeeResponse>("/manager/employees", request);
  },

  /**
   * Get employees with default pagination settings
   * @param filter - Optional filter parameters (defaults: page=0, size=20)
   * @returns Promise resolving to paginated employee data
   */
  getDefaultEmployees(filter?: Partial<EmployeeFilter>): Promise<EmployeePage> {
    const defaultFilter: EmployeeFilter = {
      page: DEFAULT_PAGE,
      size: DEFAULT_SIZE,
      sort: undefined,
      fullname: filter?.fullname,
    };

    return this.getEmployees(defaultFilter);
  },

  /**
   * Get a single employee by ID with details (superAdmin, features)
   * @param employeeId - The employee ID
   * @returns Promise resolving to employee detail data
   */
  getEmployeeDetail(employeeId: string): Promise<EmployeeDetailResponse> {
    return api.get<EmployeeDetailResponse>(`/manager/employees/${employeeId}`);
  },

  /**
   * Update an employee's superAdmin status and assigned features
   * @param employeeId - The employee ID
   * @param request - Update request containing superAdmin and featureIds
   * @returns Promise resolving to updated employee data
   */
  updateEmployee(
    employeeId: string,
    request: UpdateEmployeeRequest,
  ): Promise<EmployeeDetailResponse> {
    return api.patch<EmployeeDetailResponse>(
      `/manager/employees/${employeeId}`,
      request,
    );
  },
};