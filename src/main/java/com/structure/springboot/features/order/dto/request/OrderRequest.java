package com.structure.springboot.features.order.dto.request;

import com.structure.springboot.features.order.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * Body của {@code POST /orders}.
 *
 * <p>Không có field người đặt: lấy từ token. Không có {@code subtotal}/{@code total}: backend tự
 * tính. Không có {@code status}: đơn mới luôn là {@code PENDING}.</p>
 *
 * <p><b>Ngoại lệ có chủ đích với quy ước "DTO phẳng"</b> (đã duyệt — phương án A): {@code items}
 * là quan hệ một-nhiều bắt buộc phải lồng, không có cách nào trải phẳng một giỏ hàng n dòng.</p>
 */
public record OrderRequest(
        @NotBlank(message = "Receiver name is required")
        @Size(max = 150, message = "Receiver name must not exceed 150 characters")
        String receiverName,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^0\\d{8,10}$", message = "Phone must start with 0 and contain 9 to 11 digits")
        String phone,

        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email,

        @NotBlank(message = "Address is required")
        @Size(max = 500, message = "Address must not exceed 500 characters")
        String address,

        @Size(max = 500, message = "Note must not exceed 500 characters")
        String note,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        // @Valid để Bean Validation đi xuống kiểm tra từng phần tử bên trong; thiếu nó thì
        // ràng buộc trên OrderItemRequest hoàn toàn không chạy.
        @Valid
        @NotEmpty(message = "Order must contain at least one item")
        // Chặn giỏ phình vô hạn: mỗi dòng là một cặp (sản phẩm, size), 50 dòng đã quá thừa cho
        // một đơn quần áo. Không có trần thì client gửi 10.000 dòng là một câu IN(...) khổng lồ.
        @Size(max = 50, message = "Order must not contain more than 50 different items")
        List<OrderItemRequest> items
) {
}
