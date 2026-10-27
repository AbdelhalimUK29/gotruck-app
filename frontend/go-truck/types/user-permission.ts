/**
 * Feature role enum matching backend FeatureRoleEnum
 */
export enum FeatureRoleEnum {
  RESERVATION = "RESERVATION",
  CLIENTS = "CLIENTS",
  TRUCKS = "TRUCKS",
  LONG_TIME_RENTAL = "LONG_TIME_RENTAL",
  RENTAL = "RENTAL",
  ADMIN = "ADMIN",
  FINANCIAL_REPORTING = "FINANCIAL_REPORTING",
}

/**
 * Feature response from GET /manager/features endpoint
 */
export interface FeatureResponse {
  id: number;
  name: FeatureRoleEnum;
}

/**
 * User profile with features from GET /manager/me endpoint
 */
export interface UserProfileWithFeaturesResponse {
  id: number;
  keycloakId: string;
  companyId: number;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  owner: boolean;
  superAdmin: boolean;
  features: FeatureRoleEnum[];
}
