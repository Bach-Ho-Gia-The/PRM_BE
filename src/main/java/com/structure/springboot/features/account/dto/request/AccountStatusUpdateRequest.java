package com.structure.springboot.features.account.dto.request;

import com.structure.springboot.features.account.enums.AccountStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Admin đổi trạng thái một tài khoản.
 *
 * <p>Tách khỏi {@code AccountUpdateRequest} vì đây là hành động khác hẳn về hệ quả: sửa hồ sơ chỉ
 * đổi dữ liệu, còn đổi trạng thái là đá người ta ra khỏi mọi thiết bị. Gộp chung thì một lần sửa
 * email lỡ tay kèm {@code status} là khoá nhầm người.</p>
 *
 * <p>{@code lockedUntil} chỉ có nghĩa khi {@code status = LOCKED}; để trống nghĩa là khoá vô thời
 * hạn. Gửi kèm với trạng thái khác thì service tự bỏ qua chứ không báo lỗi.</p>
 */
public record AccountStatusUpdateRequest(

        @NotNull(message = "Status is required")
        AccountStatus status,

        @Future(message = "Locked until must be a future moment")
        Instant lockedUntil
) {
}
