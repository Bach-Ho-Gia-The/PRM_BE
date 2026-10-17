package com.structure.springboot.features.category.dto.request;

import com.structure.springboot.infrastructure.persistence.RecordStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /categories/admin/{id}: tên thay đổi + slug thiếu/null/"" sẽ sinh lại slug;
 * nếu không đổi tên, slug thiếu/null giữ nguyên; slug tường minh cho phép đổi độc lập.
 * status=DELETED không hợp lệ qua PATCH (dùng DELETE và kiểm tra Product).
 */
public record CategoryUpdateRequest(
        @Size(max = 100, message = "Category name must not exceed 100 characters")
        String categoryName,

        @Size(max = 100, message = "Slug must not exceed 100 characters")
        @Pattern(regexp = "^([a-z0-9]+(-[a-z0-9]+)*)?$", message = "Slug must be empty to regenerate or use lowercase letters, digits and hyphens")
        String slug,

        RecordStatus status
) {
}
