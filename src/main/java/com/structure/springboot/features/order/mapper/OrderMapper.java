package com.structure.springboot.features.order.mapper;

import com.structure.springboot.features.order.dto.response.OrderItemResponse;
import com.structure.springboot.features.order.dto.response.OrderResponse;
import com.structure.springboot.features.order.dto.response.OrderSummaryResponse;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.entity.OrderItemEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    /**
     * @param paidAt thời điểm đã trả tiền, {@code null} nếu chưa. Truyền riêng vì nó thuộc feature
     *               payment, không nằm trên {@link OrderEntity} — mapper cố ý không tự đi lấy, để
     *               tầng nào quyết định việc gọi payment service.
     *               <p>{@code paymentDueAt} thì KHÔNG cần truyền: nó là field cùng tên trên entity
     *               nên MapStruct tự map.</p>
     *               <p><b>Vì sao {@code source} phải ghi {@code entity.account.id} chứ không
     *               {@code account.id}:</b> khi method có từ hai tham số trở lên, MapStruct bắt buộc
     *               mọi đường dẫn nguồn phải bắt đầu bằng <b>tên tham số</b> — nếu không nó không
     *               biết lấy {@code account} từ {@code entity} hay từ {@code paidAt}. Ghi thiếu tiền
     *               tố là lỗi biên dịch, không phải lỗi lúc chạy.</p>
     */
    @Mapping(target = "accountId", source = "entity.account.id")
    OrderResponse toResponse(OrderEntity entity, Instant paidAt);

    @Mapping(target = "productId", source = "product.id")
    OrderItemResponse toItemResponse(OrderItemEntity entity);

    /** Đọc thẳng cột {@code totalItems} — KHÔNG được cộng từ {@code items} vì quan hệ đó lazy. */
    OrderSummaryResponse toSummaryResponse(OrderEntity entity);
}
