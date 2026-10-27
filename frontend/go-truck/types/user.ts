export interface UserProfileResponse {
  id: number;
  keycloakId: string;
  companyId: number | null;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  owner: boolean;
  superAdmin: boolean;
}
