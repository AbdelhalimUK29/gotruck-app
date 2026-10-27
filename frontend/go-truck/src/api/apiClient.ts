import keycloak from "@/src/auth/keycloak";

const API_URL = process.env.NEXT_PUBLIC_API_URL!;

export type ApiHeaders = Record<string, string>;

export type FieldErrors = Record<string, string>;

export interface ApiErrorOptions {
  status: number;
  message: string;
  fieldErrors?: FieldErrors;
  cause?: unknown;
}

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: FieldErrors;
  readonly cause?: unknown;

  constructor({ status, message, fieldErrors = {}, cause }: ApiErrorOptions) {
    super(message);

    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;

    if (cause !== undefined) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (this as any).cause = cause;
    }
  }
}

async function getAccessToken(): Promise<string> {
  if (!keycloak.authenticated) {
    throw new ApiError({
      status: 401,
      message: "Not authenticated",
      fieldErrors: {},
    });
  }

  await keycloak.updateToken(30);

  if (!keycloak.token) {
    throw new ApiError({
      status: 401,
      message: "Access token unavailable",
      fieldErrors: {},
    });
  }

  return keycloak.token;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function extractMessage(payload: unknown): string {
  if (!isRecord(payload)) return "";

  const message = payload.message;
  if (typeof message === "string" && message.length > 0) return message;

  return "";
}

function extractFieldErrors(payload: unknown): FieldErrors {
  if (!isRecord(payload)) return {};

  const source: unknown =
    payload.fieldErrors ?? payload.errors ?? payload;

  if (!isRecord(source)) return {};

  const result: FieldErrors = {};

  for (const [key, value] of Object.entries(source)) {
    if (typeof value === "string" && value.length > 0) {
      result[key] = value;
    }
  }

  return result;
}

function parseErrorMessage(status: number): string | undefined {
  if (status === 401 || status === 403) {
    return "Your session has expired. Please sign in again.";
  }

  return undefined;
}

async function request<T>(
  endpoint: string,
  options: RequestInit & { cache?: RequestCache } = {},
): Promise<T> {
  let token: string;
  try {
    token = await getAccessToken();
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError({
      status: 401,
      message: "Failed to get access token",
      fieldErrors: {},
      cause: error,
    });
  }

  const headers = new Headers(options.headers);

  headers.set("Authorization", `Bearer ${token}`);
  headers.set("Content-Type", "application/json");

  let response: Response;
  try {
    response = await fetch(`${API_URL}${endpoint}`, {
      ...options,
      headers,
    });
  } catch (cause) {
    throw new ApiError({
      status: 0,
      message:
        "Unable to reach the server. Please check your connection and try again.",
      fieldErrors: {},
      cause,
    });
  }

  if (!response.ok) {
    let payload: unknown;
    try {
      payload = await response.json();
    } catch {
      payload = await response.text();
    }

    const message =
      parseErrorMessage(response.status) ??
      extractMessage(payload) ??
      "Something went wrong. Please try again.";

    throw new ApiError({
      status: response.status,
      message,
      fieldErrors: extractFieldErrors(payload),
      cause: payload,
    });
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

async function requestMultipart<T>(
  endpoint: string,
  formData: FormData,
  headers?: ApiHeaders,
): Promise<T> {
  let token: string;
  try {
    token = await getAccessToken();
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError({
      status: 401,
      message: "Failed to get access token",
      fieldErrors: {},
      cause: error,
    });
  }

  const requestHeaders = new Headers(headers);
  requestHeaders.set("Authorization", `Bearer ${token}`);
  // Do NOT set Content-Type: browser will set it with the correct boundary

  let response: Response;
  try {
    response = await fetch(`${API_URL}${endpoint}`, {
      method: "POST",
      body: formData,
      headers: requestHeaders,
    });
  } catch (cause) {
    throw new ApiError({
      status: 0,
      message:
        "Unable to reach the server. Please check your connection and try again.",
      fieldErrors: {},
      cause,
    });
  }

  if (!response.ok) {
    let payload: unknown;
    try {
      payload = await response.json();
    } catch {
      payload = await response.text();
    }

    const message =
      parseErrorMessage(response.status) ??
      extractMessage(payload) ??
      "Something went wrong. Please try again.";

    throw new ApiError({
      status: response.status,
      message,
      fieldErrors: extractFieldErrors(payload),
      cause: payload,
    });
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

export const api = {
  get<T>(endpoint: string, headers?: ApiHeaders) {
    return request<T>(endpoint, { headers });
  },

  post<T>(endpoint: string, body: unknown, headers?: ApiHeaders) {
    return request<T>(endpoint, {
      method: "POST",
      body: JSON.stringify(body),
      headers,
    });
  },

  put<T>(endpoint: string, body: unknown, headers?: ApiHeaders) {
    return request<T>(endpoint, {
      method: "PUT",
      body: JSON.stringify(body),
      headers,
    });
  },

  patch<T>(endpoint: string, body: unknown, headers?: ApiHeaders) {
    return request<T>(endpoint, {
      method: "PATCH",
      body: JSON.stringify(body),
      headers,
    });
  },

  delete<T>(endpoint: string, headers?: ApiHeaders) {
    return request<T>(endpoint, {
      method: "DELETE",
      headers,
    });
  },

  uploadFile<T>(endpoint: string, formData: FormData, headers?: ApiHeaders) {
    return requestMultipart<T>(endpoint, formData, headers);
  },
};
