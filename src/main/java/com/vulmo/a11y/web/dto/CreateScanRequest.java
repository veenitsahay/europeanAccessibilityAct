package com.vulmo.a11y.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateScanRequest(
        @NotBlank String url,
        @Min(1) @Max(50) Integer maxPages,
        String orgName,
        String siteName,
        @Email String contactEmail) {
}
