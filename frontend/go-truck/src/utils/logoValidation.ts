/**
 * Logo validation constants and utilities
 * Aligns with backend validation: max 10MB, 1024x1024px
 */

export const LOGO_VALIDATION = {
  MAX_SIZE_MB: 10,
  MAX_SIZE_BYTES: 10 * 1024 * 1024,
  MAX_WIDTH: 1024,
  MAX_HEIGHT: 1024,
  ALLOWED_MIME_TYPES: ['image/jpeg', 'image/png', 'image/webp', 'image/svg+xml'] as const,
} as const;

export type AllowedMimeType = (typeof LOGO_VALIDATION.ALLOWED_MIME_TYPES)[number];

export interface ValidationResult {
  valid: boolean;
  error?: string;
}

export interface ImageDimensions {
  width: number;
  height: number;
  error?: string;
}

/**
 * Validate file size
 */
export function validateFileSize(file: File): ValidationResult {
  if (file.size > LOGO_VALIDATION.MAX_SIZE_BYTES) {
    return {
      valid: false,
      error: `File too large (max ${LOGO_VALIDATION.MAX_SIZE_MB}MB, got ${(file.size / 1024 / 1024).toFixed(2)}MB)`,
    };
  }
  return { valid: true };
}

/**
 * Validate MIME type
 */
export function validateMimeType(file: File): ValidationResult {
  if (!LOGO_VALIDATION.ALLOWED_MIME_TYPES.includes(file.type as AllowedMimeType)) {
    return {
      valid: false,
      error: `Invalid file format. Allowed: JPEG, PNG, WebP, SVG`,
    };
  }
  return { valid: true };
}

/**
 * Get image dimensions
 * Returns dimensions or error if file cannot be read as image
 */
export async function getImageDimensions(file: File): Promise<ImageDimensions> {
  return new Promise((resolve) => {
    const reader = new FileReader();

    reader.onload = (e) => {
      const img = new Image();
      img.onload = () => {
        resolve({
          width: img.width,
          height: img.height,
        });
      };
      img.onerror = () => {
        resolve({
          width: 0,
          height: 0,
          error: 'Could not read image dimensions',
        });
      };
      img.src = e.target?.result as string;
    };

    reader.onerror = () => {
      resolve({
        width: 0,
        height: 0,
        error: 'Could not read file',
      });
    };

    reader.readAsDataURL(file);
  });
}

/**
 * Validate image dimensions
 */
export function validateImageDimensions(dimensions: ImageDimensions): ValidationResult {
  if (dimensions.error) {
    return {
      valid: false,
      error: dimensions.error,
    };
  }

  if (dimensions.width > LOGO_VALIDATION.MAX_WIDTH || dimensions.height > LOGO_VALIDATION.MAX_HEIGHT) {
    return {
      valid: false,
      error: `Image dimensions must be ≤ ${LOGO_VALIDATION.MAX_WIDTH}×${LOGO_VALIDATION.MAX_HEIGHT}px (got ${dimensions.width}×${dimensions.height}px)`,
    };
  }

  return { valid: true };
}

/**
 * Comprehensive logo file validation
 * Runs: file size → MIME type → image dimensions
 */
export async function validateLogo(file: File): Promise<ValidationResult> {
  // Check file size
  const sizeCheck = validateFileSize(file);
  if (!sizeCheck.valid) return sizeCheck;

  // Check MIME type
  const mimeCheck = validateMimeType(file);
  if (!mimeCheck.valid) return mimeCheck;

  // Check image dimensions
  const dimensions = await getImageDimensions(file);
  const dimensionCheck = validateImageDimensions(dimensions);
  if (!dimensionCheck.valid) return dimensionCheck;

  return { valid: true };
}
