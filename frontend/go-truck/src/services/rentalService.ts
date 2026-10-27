import { api } from "@/src/api/apiClient";
import type {
  RentalResponse,
  RentalFilter,
  CreateRentalRequest,
  RentalPage,
  UpdateRentalRequest,
} from "@/types/rental";

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 20;

/**
 * Rental service for managing rental operations
 */
export const rentalService = {
  /**
   * Fetch rentals with pagination and filtering
   * @param filter - Filter parameters (name search, pagination, sorting)
   * @returns Promise resolving to paginated rental data
   */
  getRentals(filter: RentalFilter): Promise<RentalPage> {
    // Build query string from filter
    const queryParams = new URLSearchParams();
    queryParams.append("page", String(filter.page ?? 0));
    queryParams.append("size", String(filter.size ?? 20));
    if (filter.name) queryParams.append("name", filter.name);
    if (filter.sort) queryParams.append("sort", filter.sort);

    const query = queryParams.toString();
    const endpoint = `/manager/rentals${query ? `?${query}` : ""}`;

    return api.get<RentalPage>(endpoint);
  },

  /**
   * Create a new rental
   * @param request - Rental creation request data
   * @returns Promise resolving to created rental data
   */
  createRental(request: CreateRentalRequest): Promise<RentalResponse> {
    return api.post<RentalResponse>("/manager/rentals", request);
  },

  /**
   * Get rentals with default pagination settings
   * @param filter - Optional filter parameters (defaults: page=0, size=20)
   * @returns Promise resolving to paginated rental data
   */
  getDefaultRentals(filter?: Partial<RentalFilter>): Promise<RentalPage> {
    const defaultFilter: RentalFilter = {
      page: DEFAULT_PAGE,
      size: DEFAULT_SIZE,
      sort: undefined,
      name: filter?.name,
    };

    return this.getRentals(defaultFilter);
  },

  /**
   * Get a single rental by ID
   * @param rentalId - The rental ID
   * @returns Promise resolving to rental data
   */
  getRentalDetail(rentalId: string): Promise<RentalResponse> {
    return api.get<RentalResponse>(`/manager/rentals/${rentalId}`);
  },

  /**
   * Update a rental
   * @param rentalId - The rental ID
   * @param request - Update request containing rental fields
   * @returns Promise resolving to updated rental data
   */
  updateRental(
    rentalId: string,
    request: UpdateRentalRequest,
  ): Promise<RentalResponse> {
    return api.patch<RentalResponse>(`/manager/rentals/${rentalId}`, request);
  },

  /**
   * Delete a rental
   * @param rentalId - The rental ID
   * @returns Promise resolving when rental is deleted
   */
  deleteRental(rentalId: string): Promise<void> {
    return api.delete<void>(`/manager/rentals/${rentalId}`);
  },

  /**
   * Assign one or more users to a rental
   * @param rentalId - The rental ID
   * @param userIds - Array of user IDs to assign
   * @returns Promise resolving when users are assigned
   */
  assignUsersToRental(rentalId: string, userIds: string[]): Promise<void> {
    const queryParams = new URLSearchParams();
    for (const userId of userIds) {
      queryParams.append("userIds", userId);
    }
    const query = queryParams.toString();
    return api.post<void>(
      `/manager/rentals/${rentalId}/users${query ? `?${query}` : ""}`,
      {},
    );
  },

  /**
   * Remove one or more users from a rental
   * @param rentalId - The rental ID
   * @param userIds - Array of user IDs to remove
   * @returns Promise resolving when users are removed
   */
  removeUsersFromRental(rentalId: string, userIds: string[]): Promise<void> {
    const queryParams = new URLSearchParams();
    for (const userId of userIds) {
      queryParams.append("userIds", userId);
    }
    const query = queryParams.toString();
    return api.delete<void>(
      `/manager/rentals/${rentalId}/users${query ? `?${query}` : ""}`,
    );
  },
};
