package com.structure.springboot.features.order.service;

import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.order.dto.request.OrderRequest;
import com.structure.springboot.features.order.dto.response.OrderResponse;
import com.structure.springboot.features.order.dto.response.OrderSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {

    /**
     * Đặt hàng. Backend tự tra giá từ DB, tự tính tiền và tự trừ tồn kho — mọi con số tiền bạc
     * client gửi lên đều bị bỏ qua.
     */
    OrderResponse createOrder(UUID accountId, OrderRequest request);

    /** Danh sách đơn của chính mình, mới nhất trước. */
    PageResponse<OrderSummaryResponse> getMyOrders(UUID accountId, Pageable pageable);

    /**
     * Chi tiết một đơn của chính mình.
     *
     * <p>Mã đơn của người khác trả <b>404</b> chứ không phải 403: 403 là gián tiếp xác nhận
     * "mã đơn này có thật", đủ để người ta dò ra mã đơn hợp lệ.</p>
     */
    OrderResponse getMyOrderByNumber(UUID accountId, String orderNumber);
}
