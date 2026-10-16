package com.structure.springboot.features.category.dto.request;

import com.structure.springboot.infrastructure.persistence.RecordStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code PATCH /categories/{id}} — cập nhật MỘT PHẦN.
 * Field nào không gửi (hoặc gửi null) thì giữ nguyên giá trị cũ trong DB.
 */
public record CategoryUpdateRequest(
        @Size(max = 100, message = "Category name must not exceed 100 characters")
        String categoryName,

        @Size(max = 100, message = "Slug must not exceed 100 characters")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug may contain only lowercase letters, digits and hyphens")
        String slug,

        RecordStatus status
) {
}
