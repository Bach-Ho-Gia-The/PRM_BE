package com.structure.springboot.features.order.dto.request;

import jakarta.validation.constraints.Size;

/** Lý do huỷ là tuỳ chọn: bắt nhập chỉ làm khách bỏ cuộc giữa chừng. */
public record OrderCancelRequest(
        @Size(max = 300, message = "Cancel reason must not exceed 300 characters")
        String reason
) {
}
