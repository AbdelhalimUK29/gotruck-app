/**
 * MinIO storage utilities
 * Constructs URLs for accessing files stored in MinIO S3-compatible storage
 */

const MINIO_URL = process.env.NEXT_PUBLIC_MINIO_URL || "http://localhost:9000";
const MINIO_BUCKET = process.env.NEXT_PUBLIC_MINIO_BUCKET || "gotruck-media-public";

/**
 * Constructs a full MinIO URL from a file path
 * Handles both relative paths (just filename) and full paths
 *
 * @param filePath - The file path or name stored in MinIO (e.g., "company-logos/abc123.png" or just the object name)
 * @returns Full MinIO URL to access the file
 *
 * @example
 * getMinIOUrl("company-logos/abc123.png")
 * // Returns: "http://localhost:9000/gotruck-media-public/company-logos/abc123.png"
 */
export function getMinIOUrl(filePath?: string): string | undefined {
  if (!filePath) return undefined;

  // Remove leading/trailing slashes
  const cleanPath = filePath.replace(/^\/+|\/+$/g, "");

  if (!cleanPath) return undefined;

  return `${MINIO_URL}/${MINIO_BUCKET}/${cleanPath}`;
}

/**
 * Check if a URL is already a full MinIO URL
 */
export function isMinIOUrl(url?: string): boolean {
  if (!url) return false;
  return url.startsWith("http://") || url.startsWith("https://");
}

/**
 * Get display URL for a logo
 * If it's a full URL, return as-is; if it's a path, construct MinIO URL
 */
export function getLogoUrl(logoPath?: string): string | undefined {
  if (!logoPath) return undefined;

  // If it's already a full URL, return as-is
  if (isMinIOUrl(logoPath)) {
    return logoPath;
  }

  // Otherwise construct MinIO URL
  return getMinIOUrl(logoPath);
}
