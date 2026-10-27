"use client";

import { useRef, useState } from "react";
import { validateLogo } from "@/src/utils/logoValidation";
import { getLogoUrl } from "@/src/utils/minioUrl";
import { companyService } from "@/src/services/companyService";
import { ApiError } from "@/src/api/apiClient";

interface LogoUploadProps {
  companyId: string;
  currentLogo?: string;
  onUploadStart?: () => void;
  onUploadSuccess?: () => void;
  onUploadError?: (error: string) => void;
}

export default function LogoUpload({
  companyId,
  currentLogo,
  onUploadStart,
  onUploadSuccess,
  onUploadError,
}: LogoUploadProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dragActive, setDragActive] = useState(false);

  const handleFileSelect = async (file: File) => {
    setError(null);

    // Validate logo
    const validation = await validateLogo(file);
    if (!validation.valid) {
      setError(validation.error || "Validation failed");
      setSelectedFile(null);
      setPreview(null);
      onUploadError?.(validation.error || "Validation failed");
      return;
    }

    // Create preview
    const reader = new FileReader();
    reader.onload = (e) => {
      setPreview(e.target?.result as string);
    };
    reader.readAsDataURL(file);

    setSelectedFile(file);
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      handleFileSelect(file);
    }
  };

  const handleDrag = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setDragActive(true);
    } else if (e.type === "dragleave") {
      setDragActive(false);
    }
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);

    const file = e.dataTransfer.files?.[0];
    if (file) {
      handleFileSelect(file);
    }
  };

  const handleUpload = async () => {
    if (!selectedFile) return;

    setUploading(true);
    setError(null);
    onUploadStart?.();

    try {
      const response = await companyService.uploadLogo(companyId, selectedFile);
        setSelectedFile(null);
        if (fileInputRef.current) {
          fileInputRef.current.value = "";
        }
        onUploadSuccess?.();
      
    } catch (err) {
      let errorMessage = "Failed to upload logo";

      if (err instanceof ApiError) {
        errorMessage = err.message;
      } else if (err instanceof Error) {
        errorMessage = err.message;
      }

      setError(errorMessage);
      onUploadError?.(errorMessage);
    } finally {
      setUploading(false);
    }
  };

  const handleClear = () => {
    setSelectedFile(null);
    setPreview(null);
    setError(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const displayPreview = preview || getLogoUrl(currentLogo);
  const hasSelectedFile = selectedFile !== null;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex gap-4">
        {/* Preview Section */}
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium text-text-primary">Logo Preview</label>
          <div className="w-32 h-32 rounded-lg border border-border bg-gray-50 flex items-center justify-center overflow-hidden">
            {displayPreview ? (
              <img
                src={displayPreview}
                alt="Company logo"
                className="w-full h-full object-contain p-2"
              />
            ) : (
              <div className="text-center">
                <svg
                  className="w-12 h-12 text-text-secondary mx-auto"
                  fill="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" />
                </svg>
                <p className="text-xs text-text-secondary mt-2">No logo</p>
              </div>
            )}
          </div>
        </div>

        {/* Upload Section */}
        <div className="flex-1 flex flex-col gap-2">
          <label className="text-sm font-medium text-text-primary">Upload Logo</label>

          {/* Drop Zone */}
          <div
            onDragEnter={handleDrag}
            onDragLeave={handleDrag}
            onDragOver={handleDrag}
            onDrop={handleDrop}
            className={`relative rounded-lg border-2 border-dashed p-6 text-center transition-colors cursor-pointer ${
              dragActive
                ? "border-primary bg-blue-50"
                : "border-border hover:border-primary hover:bg-gray-50"
            } ${uploading ? "opacity-50 cursor-not-allowed" : ""}`}
            onClick={() => !uploading && fileInputRef.current?.click()}
            onKeyDown={(e) => {
              if ((e.key === "Enter" || e.key === " ") && !uploading) {
                fileInputRef.current?.click();
              }
            }}
            role="button"
            tabIndex={uploading ? -1 : 0}
            aria-label="Click or drag to upload logo"
          >
            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp,image/svg+xml"
              onChange={handleInputChange}
              disabled={uploading}
              className="hidden"
              aria-label="Logo file input"
            />

            {uploading ? (
              <>
                <div className="text-sm text-text-secondary">Uploading...</div>
              </>
            ) : (
              <>
                <svg
                  className="w-8 h-8 text-text-secondary mx-auto mb-2"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                  strokeWidth={1.5}
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M12 16.5V9.75m0 0l3 3m-3-3l-3 3M6.75 19.5a4.5 4.5 0 01-1.41-8.775 5.25 5.25 0 1110.233-2.33A3 3 0 0016.5 19.5H6.75z"
                  />
                </svg>
                <div className="text-sm text-text-primary font-medium">
                  {hasSelectedFile ? "Ready to upload" : "Click or drag to upload"}
                </div>
                <div className="text-xs text-text-secondary mt-1">
                  JPEG, PNG, WebP, SVG • Max 10MB • Max 1024×1024px
                </div>
              </>
            )}
          </div>

          {/* Error Message */}
          {error && (
            <div className="rounded-lg bg-red-50 p-3 border border-red-200">
              <p className="text-sm text-red-800">{error}</p>
            </div>
          )}

          {/* Action Buttons */}
          <div className="flex gap-2">
            {hasSelectedFile ? (
              <>
                <button
                  onClick={handleUpload}
                  disabled={uploading}
                  className="flex-1 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
                >
                  {uploading ? "Uploading..." : "Upload"}
                </button>
                <button
                  onClick={handleClear}
                  disabled={uploading}
                  className="flex-1 rounded-lg border border-border px-3 py-2 text-sm font-medium text-text-primary hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
                >
                  Cancel
                </button>
              </>
            ) : (
              displayPreview && (
                <button
                  onClick={handleClear}
                  className="w-full rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-medium text-red-800 hover:bg-red-100 transition-colors"
                >
                  Remove Logo
                </button>
              )
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
