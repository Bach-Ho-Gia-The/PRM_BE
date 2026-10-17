package com.structure.springboot.features.order.service;

import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.order.dto.request.OrderCancelRequest;
import com.structure.springboot.features.order.dto.request.OrderRequest;
import com.structure.springboot.features.order.dto.request.OrderStatusUpdateRequest;
import com.structure.springboot.features.order.enums.OrderStatus;
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

    /**
     * Tài khoản này còn đơn hàng nào không.
     *
     * <p>Mở ra cho module tài khoản hỏi trước khi xoá. Không để {@code AccountServiceImpl} tiêm
     * thẳng {@code OrderRepository}: dữ liệu của module nào thì module đó giữ, bên ngoài đi qua
     * service — đúng hướng phụ thuộc mà {@code profileService} đang dùng ở cùng chỗ đó.</p>
     */
    boolean hasOrders(UUID accountId);

    /** Màn quản trị: mọi đơn của mọi người, lọc theo trạng thái nếu có. */
    PageResponse<OrderSummaryResponse> getOrders(OrderStatus status, Pageable pageable);

    /** Màn quản trị: chi tiết một đơn bất kỳ, tra theo id chứ không theo mã đơn. */
    OrderResponse getOrderById(UUID id);

    /**
     * Màn quản trị: tra một đơn bất kỳ theo mã khách đọc được.
     *
     * <p>Khách gọi điện thì đọc {@code LBVN-2610-A7K2} chứ không đọc UUID. Thiếu đường này thì
     * admin phải lật từng trang danh sách để tìm.</p>
     */
    OrderResponse getOrderByNumber(String orderNumber);

    /**
     * Admin chuyển trạng thái đơn. Huỷ đơn sẽ hoàn kho.
     */
    OrderResponse updateStatus(UUID id, OrderStatusUpdateRequest request);

    /**
     * Khách tự huỷ đơn của chính mình.
     *
     * <p>Chỉ huỷ được khi đơn còn {@code PENDING}: từ {@code CONFIRMED} trở đi shop đã gói hàng,
     * muốn huỷ thì nhắn shop. Tra theo {@code orderNumber} + tài khoản nên không ai huỷ được đơn
     * của người khác; không tìm thấy thì trả 404 chứ không 403 để khỏi lộ mã đơn có tồn tại.</p>
     */
    OrderResponse cancelMyOrder(UUID accountId, String orderNumber, OrderCancelRequest request);
}
