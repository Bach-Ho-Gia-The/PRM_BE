package com.structure.springboot.features.store.dto.request;

import com.structure.springboot.infrastructure.persistence.RecordStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH: field null nghĩa là "không đổi". Không có @NotBlank/@NotNull ở đây. */
public record StoreUpdateRequest(
        @Size(max = 150, message = "Store name must not exceed 150 characters")
        String storeName,

        @Size(max = 300, message = "Address must not exceed 300 characters")
        String address,

        @Pattern(regexp = "^0\\d{9}$", message = "Phone must be 10 digits starting with 0")
        String phone,

        @Size(max = 100, message = "Opening hours must not exceed 100 characters")
        String openingHours,

        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        Double longitude,

        @Size(max = 500, message = "Image URL must not exceed 500 characters")
        String imageUrl,

        RecordStatus status
) {
}
