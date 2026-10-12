package com.structure.springboot.features.order.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.order.config.OrderProperties;
import com.structure.springboot.features.order.dto.request.OrderItemRequest;
import com.structure.springboot.features.order.dto.request.OrderRequest;
import com.structure.springboot.features.order.dto.response.OrderResponse;
import com.structure.springboot.features.order.dto.response.OrderSummaryResponse;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.entity.OrderItemEntity;
import com.structure.springboot.features.order.enums.PaymentMethod;
import com.structure.springboot.features.order.mapper.OrderMapper;
import com.structure.springboot.features.order.repository.OrderRepository;
import com.structure.springboot.features.order.service.OrderService;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.entity.ProductVariantEntity;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.repository.ProductVariantRepository;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /**
     * Bỏ các ký tự dễ nhìn nhầm khi khách đọc mã qua điện thoại: I/1, O/0.
     * Dùng ở đúng một chỗ nhưng giữ làm hằng vì chính bộ ký tự này là quy ước của mã đơn.
     */
    private static final String ORDER_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom random = new SecureRandom();

    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderMapper orderMapper;
    private final OrderProperties orderProperties;

    @Override
    @Transactional
    public OrderResponse createOrder(UUID accountId, OrderRequest request) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Account no longer exists"));

        OrderEntity order = OrderEntity.builder()
                .orderNumber(generateOrderNumber())
                .account(account)
                .paymentMethod(request.paymentMethod())
                .receiverName(request.receiverName().trim())
                .phone(request.phone().trim())
                .email(request.email())
                .address(request.address().trim())
                .note(request.note())
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        int totalItems = 0;

        for (OrderItemRequest line : mergeDuplicates(request.items())) {
            OrderItemEntity item = buildItem(line, order);
            order.getItems().add(item);

            subtotal = subtotal.add(item.getLineTotal());
            totalItems += item.getQuantity();
        }

        BigDecimal shippingFee = resolveShippingFee(request.paymentMethod(), subtotal);

        order.setSubtotal(subtotal);
        order.setShippingFee(shippingFee);
        // Mã giảm giá chưa làm (thuộc đợt sau). Để ZERO chứ không null vì cột NOT NULL và
        // mọi phép cộng BigDecimal với null đều NPE.
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(subtotal.add(shippingFee));
        order.setTotalItems(totalItems);

        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getMyOrders(UUID accountId, Pageable pageable) {
        return PageResponse.from(orderRepository.findByAccountId(accountId, pageable)
                .map(orderMapper::toSummaryResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getMyOrderByNumber(UUID accountId, String orderNumber) {
        return orderRepository.findByOrderNumberAndAccountId(orderNumber.trim().toUpperCase(Locale.ROOT), accountId)
                .map(orderMapper::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found: " + orderNumber));
    }

    /**
     * Gộp các dòng trùng {@code productId + size} thành một.
     *
     * <p>Frontend giữ giỏ hàng ở localStorage nên hoàn toàn có thể gửi lên hai dòng cùng một mẫu
     * cùng một size. Không gộp thì đơn hiện hai dòng y hệt nhau, khách tưởng bị tính tiền hai lần.</p>
     *
     * <p>{@code LinkedHashMap} để giữ nguyên thứ tự khách đã thêm vào giỏ.</p>
     */
    private Collection<OrderItemRequest> mergeDuplicates(List<OrderItemRequest> items) {
        Map<String, OrderItemRequest> merged = new LinkedHashMap<>();

        for (OrderItemRequest item : items) {
            String key = item.productId() + "_" + item.size();
            merged.merge(key, item, (existing, incoming) -> new OrderItemRequest(
                    existing.productId(),
                    existing.size(),
                    existing.quantity() + incoming.quantity()
            ));
        }
        return merged.values();
    }

    /** Tra sản phẩm, kiểm tồn kho, trừ kho và chụp lại thông tin hiển thị vào dòng hàng. */
    private OrderItemEntity buildItem(OrderItemRequest request, OrderEntity order) {
        ProductEntity product = productRepository.findById(request.productId())
                .filter(p -> p.getStatus() == null || p.getStatus() == RecordStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Product not available: " + request.productId()));

        ProductVariantEntity variant = productVariantRepository
                .findByProductIdAndSize(product.getId(), request.size())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Size " + request.size() + " is not available for product: " + product.getName()));

        if (variant.getStockQuantity() < request.quantity()) {
            throw new ApiException(ErrorCode.CONFLICT, "Only " + variant.getStockQuantity()
                    + " item(s) left in stock for " + product.getName() + " size " + request.size());
        }
        variant.setStockQuantity(variant.getStockQuantity() - request.quantity());

        BigDecimal unitPrice = product.getPrice();
        return OrderItemEntity.builder()
                .order(order)
                .product(product)
                // Chụp lại tên/ảnh/giá tại thời điểm đặt: shop đổi giá hay đổi ảnh về sau cũng
                // không được phép viết lại hoá đơn cũ.
                .productName(product.getName())
                .productSlug(product.getSlug())
                .imageUrl(product.getThumbnail())
                .size(request.size())
                .unitPrice(unitPrice)
                .quantity(request.quantity())
                .lineTotal(unitPrice.multiply(BigDecimal.valueOf(request.quantity())))
                .build();
    }

    /**
     * Miễn phí ship khi chuyển khoản (khuyến khích trả trước, shop đỡ rủi ro bom hàng) hoặc khi
     * đơn đạt ngưỡng. Hai điều kiện lấy từ đúng luật mà hai app frontend đang hiển thị.
     */
    private BigDecimal resolveShippingFee(PaymentMethod paymentMethod, BigDecimal subtotal) {
        boolean freeShipping = paymentMethod == PaymentMethod.BANK_TRANSFER
                || subtotal.compareTo(orderProperties.freeShippingThreshold()) >= 0;

        return freeShipping ? BigDecimal.ZERO : orderProperties.shippingFee();
    }

    /**
     * Sinh mã đơn dạng {@code LBVN-2510-A3KD}.
     *
     * <p>Thử lại vài lần phòng khi trùng — với 32^4 ≈ 1 triệu tổ hợp mỗi tháng thì trùng là cực
     * hiếm, nhưng cột {@code order_number} có ràng buộc unique nên trùng là cả đơn hàng bị văng.</p>
     *
     * <p>Dùng {@link SecureRandom} chứ không phải {@code Random}: {@code Random} sinh dãy đoán
     * được, người ta dò ra mã đơn của khách khác rồi thử tra.</p>
     */
    private String generateOrderNumber() {
        String period = YearMonth.now().format(DateTimeFormatter.ofPattern("yyMM"));

        // 5 lần thử, mỗi mã 4 ký tự.
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder code = new StringBuilder("LBVN-").append(period).append('-');
            for (int i = 0; i < 4; i++) {
                code.append(ORDER_CODE_ALPHABET.charAt(random.nextInt(ORDER_CODE_ALPHABET.length())));
            }

            String candidate = code.toString();
            if (!orderRepository.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, "Cannot generate a unique order number");
    }
}
