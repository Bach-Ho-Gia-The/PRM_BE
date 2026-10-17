package com.structure.springboot.features.store.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StoreRequest(
        @NotBlank(message = "Store name is required")
        @Size(max = 150, message = "Store name must not exceed 150 characters")
        String storeName,

        @NotBlank(message = "Address is required")
        @Size(max = 300, message = "Address must not exceed 300 characters")
        String address,

        @Pattern(regexp = "^0\\d{9}$", message = "Phone must be 10 digits starting with 0")
        String phone,

        @Size(max = 100, message = "Opening hours must not exceed 100 characters")
        String openingHours,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        Double longitude,

        @Size(max = 500, message = "Image URL must not exceed 500 characters")
        String imageUrl
) {
}
