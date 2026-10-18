package com.thinklab.application.dto.request;

import com.thinklab.domain.model.License.Unit;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * DTO for {@code initiate}: how many seats of a product were bought, counted in what, when to warn and how often the tool reports. The vendor
 * and the agreement are vendor registry ids and are optional.
 */
@Serdeable
public record InitiateLicenseRequest(
        @NotBlank(message = "The product is required")
        @Size(max = 80, message = "The product must not exceed 80 characters")
        String product,
        @NotNull(message = "The unit is required") Unit unit,
        @NotNull(message = "The seats bought are required") @Min(value = 1, message = "At least one seat must be bought") @Max(value = 10000000, message = "Too many seats")
        Integer entitled,
        @NotNull(message = "The warning use is required") @Min(value = 1, message = "The warning use must be at least 1 percent") @Max(value = 100, message = "The warning use must not exceed 100 percent")
        Integer warnPercent,
        @NotNull(message = "The reporting interval is required") @Min(value = 1, message = "The reporting interval must be at least 1 hour") @Max(value = 720, message = "The reporting interval must not exceed 720 hours")
        Integer reportEveryHours,
        @Nullable UUID vendorId,
        @Nullable UUID agreementId
) {}
