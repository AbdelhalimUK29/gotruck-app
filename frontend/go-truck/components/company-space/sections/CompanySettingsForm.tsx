"use client";

import type { CompanyResponse } from "@/types/company-space";
import { useState } from "react";
import { ApiError } from "@/src/api/apiClient";
import { companyService } from "@/src/services/companyService";
import FieldInput from "@/components/company-form/FieldInput";
import LogoUpload from "@/components/company-form/LogoUpload";

interface FieldErrors {
  name?: string;
  email?: string;
  website?: string;
  location?: string;
  city?: string;
  country?: string;
}

const MAX_NAME = 255;
const MAX_WEBSITE = 255;
const MAX_EMAIL = 255;
const MAX_LOCATION = 255;
const MAX_CITY = 100;
const MAX_COUNTRY = 100;
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validate(values: CompanyResponse): FieldErrors {
  const errors: FieldErrors = {};

  // Name validation
  if (!values.name || values.name.trim() === "") {
    errors.name = "Company name is required";
  } else if (values.name.length > MAX_NAME) {
    errors.name = `Company name must be ${MAX_NAME} characters or less`;
  }

  // Email validation
  if (values.email && values.email.trim() !== "") {
    if (!EMAIL_REGEX.test(values.email)) {
      errors.email = "Please enter a valid email address";
    } else if (values.email.length > MAX_EMAIL) {
      errors.email = `Email must be ${MAX_EMAIL} characters or less`;
    }
  }

  // Website validation
  if (values.website && values.website.trim() !== "") {
    if (values.website.length > MAX_WEBSITE) {
      errors.website = `Website must be ${MAX_WEBSITE} characters or less`;
    }
  }

  // Location validation
  if (values.location && values.location.length > MAX_LOCATION) {
    errors.location = `Location must be ${MAX_LOCATION} characters or less`;
  }

  // City validation
  if (values.city && values.city.length > MAX_CITY) {
    errors.city = `City must be ${MAX_CITY} characters or less`;
  }

  // Country validation
  if (values.country && values.country.length > MAX_COUNTRY) {
    errors.country = `Country must be ${MAX_COUNTRY} characters or less`;
  }

  return errors;
}

export default function CompanySettingsForm({
  company,
}: {
  company: CompanyResponse;
}) {
  const [values, setValues] = useState<CompanyResponse>(company);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [logoUploading, setLogoUploading] = useState(false);
  const [logoError, setLogoError] = useState<string | null>(null);

  const setField = (field: keyof CompanyResponse) => (value: string) => {
    setErrors({});
    setValues((prev) => ({ ...prev, [field]: value }));
  };

  const hasErrors = Object.keys(errors).length > 0;

  const handleLogoUploadStart = () => {
    setLogoUploading(true);
    setLogoError(null);
  };

  const handleLogoUploadSuccess = () => {
    setLogoUploading(false);
  };

  const handleLogoUploadError = (error: string) => {
    setLogoUploading(false);
    setLogoError(error);
  };

  const handleSave = async () => {
    const nextErrors = validate(values);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    setSaving(true);
    setFormError(null);

    try {
      await companyService.update(company.id, values);
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors((error.fieldErrors as FieldErrors) || {});
        setFormError(error.message);
      } else {
        setFormError("Something went wrong. Please try again.");
      }
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    setValues(company);
    setErrors({});
    setFormError(null);
    setLogoError(null);
  };

  return (
    <div className="w-full flex flex-col gap-3">
      <div className="rounded-xl border border-border p-4 flex flex-col gap-4">
        {/* Form Error Banner */}
        {formError && (
          <div className="rounded-lg bg-red-50 p-3 border border-red-200">
            <p className="text-sm text-red-800">{formError}</p>
          </div>
        )}

        {/* Logo Upload Section */}
        <div className="border-b border-border pb-4">
          <h3 className="text-sm font-medium text-text-primary mb-3">Company Logo</h3>
          <LogoUpload
            companyId={company.id}
            currentLogo={values.logo}
            onUploadStart={handleLogoUploadStart}
            onUploadSuccess={handleLogoUploadSuccess}
            onUploadError={handleLogoUploadError}
          />
          {logoError && (
            <div className="rounded-lg bg-red-50 p-3 border border-red-200 mt-3">
              <p className="text-sm text-red-800">{logoError}</p>
            </div>
          )}
        </div>

        {/* Company Name Field */}
        <FieldInput
          label="Company name"
          name="name"
          value={values.name}
          onChange={setField("name")}
          error={errors.name}
          required
          placeholder="Acme Logistics"
          autoComplete="organization"
        />

        {/* Email & Website (2 columns) */}
        <div className="grid grid-cols-2 gap-3">
          <FieldInput
            label="Email"
            name="email"
            type="email"
            value={values.email || ""}
            onChange={setField("email")}
            error={errors.email}
            placeholder="contact@company.com"
            autoComplete="email"
          />
          <FieldInput
            label="Website"
            name="website"
            type="url"
            value={values.website || ""}
            onChange={setField("website")}
            error={errors.website}
            placeholder="https://company.com"
            autoComplete="url"
          />
        </div>

        {/* Location & City (2 columns) */}
        <div className="grid grid-cols-2 gap-3">
          <FieldInput
            label="Location"
            name="location"
            value={values.location || ""}
            onChange={setField("location")}
            error={errors.location}
            placeholder="1234 Main St, Suite 100"
          />
          <FieldInput
            label="City"
            name="city"
            value={values.city || ""}
            onChange={setField("city")}
            error={errors.city}
            placeholder="San Francisco"
          />
        </div>

        {/* Country (full width) */}
        <FieldInput
          label="Country"
          name="country"
          value={values.country || ""}
          onChange={setField("country")}
          error={errors.country}
          placeholder="United States"
        />

        {/* Action Buttons */}
        <div className="flex gap-2 pt-2">
          <button
            onClick={handleSave}
            disabled={saving || logoUploading || hasErrors}
            className="flex-1 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
          >
            {saving ? "Saving..." : "Save Changes"}
          </button>
          <button
            onClick={handleCancel}
            disabled={saving || logoUploading}
            className="flex-1 rounded-lg border border-border px-4 py-2 text-sm font-medium text-text-primary hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}