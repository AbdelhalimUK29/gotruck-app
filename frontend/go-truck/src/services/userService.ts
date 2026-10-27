import type { UserProfileResponse } from "@/types/user";

import { api } from "@/src/api/apiClient";

export const userService = {
  getUserProfileByKeycloakId(keycloakId: string): Promise<UserProfileResponse> {
    return api.get<UserProfileResponse>(`/manager/users/profile/${keycloakId}`);
  },
};
