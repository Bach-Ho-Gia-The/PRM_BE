package com.structure.springboot.features.product.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Select an existing gallery image without uploading or deleting any file. */
public record ProductThumbnailRequest(
        @NotNull(message = "Gallery image ID is required") UUID imageId
) {
}
