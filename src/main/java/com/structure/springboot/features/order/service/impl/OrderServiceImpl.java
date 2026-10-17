package com.structure.springboot.features.order.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.order.config.OrderProperties;
import com.structure.springboot.features.order.dto.request.OrderItemRequest;
import com.structure.springboot.features.order.dto.request.OrderRequest;
import com.structure.springboot.features.order.dto.request.OrderStatusUpdateRequest;
import com.structure.springboot.features.order.dto.response.OrderResponse;
import com.structure.springboot.features.order.dto.response.OrderSummaryResponse;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.entity.OrderItemEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.order.enums.PaymentMethod;
import com.structure.springboot.features.order.mapper.OrderMapper;
import com.structure.springboot.features.order.repository.OrderRepository;
import com.structure.springboot.features.order.service.OrderService;
import com.structure.springboot.features.order.specification.OrderSpecifications;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.entity.ProductVariantEntity;
import com.structure.springboot.features.product.enums.ProductSize;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.repository.ProductVariantRepository;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /**
     * Bỏ các ký tự dễ nhìn nhầm khi khách đọc mã qua điện thoại: I/1, O/0.
     * Dùng ở đúng một chỗ nhưng giữ làm hằng vì chính bộ ký tự này là quy ước của mã đơn.
     */
    private static final String ORDER_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /**
     * Luật chuyển trạng thái đơn. Giữ nguyên dạng bảng vì đây CHÍNH LÀ luật nghiệp vụ — viết thành
     * chuỗi if rải rác thì muốn biết "từ SHIPPING đi đâu được" phải đọc cả hàm.
     *
     * <p>{@code COMPLETED} và {@code CANCELLED} là ngõ cụt, nhờ vậy huỷ đơn hai lần tự bị chặn —
     * không chặn thì mỗi lần huỷ lại cộng kho một lần, kho phình lên từ hư không.</p>
     */
    private static final Map<OrderStatus, EnumSet<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.SHIPPING, OrderStatus.CANCELLED),
            OrderStatus.SHIPPING, EnumSet.of(OrderStatus.COMPLETED),
            OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class)
    );

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

        Collection<OrderItemRequest> lines = mergeDuplicates(request.items());
        Set<UUID> productIds = lines.stream()
                .map(OrderItemRequest::productId)
                .collect(Collectors.toSet());

        // Khoá đọc Product đến khi đơn commit: admin thay ảnh phải chờ snapshot của đơn ghi xong
        // trước khi quyết định có được xóa file cũ không. Khoá theo ID tăng dần để tránh deadlock.
        // Vẫn nạp cả giỏ theo một query thay vì n+1 truy vấn.
        Map<UUID, ProductEntity> products = productRepository.findByIdInOrderByIdAsc(productIds).stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        // Khoá map là cặp (sản phẩm, size) vì đó mới là thứ định danh một dòng tồn kho.
        // getProduct().getId() KHÔNG sinh thêm câu SQL: proxy lazy vốn đã giữ sẵn khoá ngoại.
        Map<String, ProductVariantEntity> variants = productVariantRepository
                .findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(
                        variant -> variantKey(variant.getProduct().getId(), variant.getSize()),
                        Function.identity()));

        BigDecimal subtotal = BigDecimal.ZERO;
        int totalItems = 0;

        for (OrderItemRequest line : lines) {
            OrderItemEntity item = buildItem(line, order, products, variants);
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
            String key = variantKey(item.productId(), item.size());
            merged.merge(key, item, (existing, incoming) -> new OrderItemRequest(
                    existing.productId(),
                    existing.size(),
                    existing.quantity() + incoming.quantity()
            ));
        }
        return merged.values();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getOrders(OrderStatus status, Pageable pageable) {
        Specification<OrderEntity> spec = Specification.allOf(OrderSpecifications.hasStatus(status));

        return PageResponse.from(orderRepository.findAll(spec, pageable).map(orderMapper::toSummaryResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        return orderMapper.toResponse(orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found with id: " + id)));
    }

    /**
     * Chuyển trạng thái đơn theo đúng bảng {@link #ALLOWED_TRANSITIONS}.
     *
     * <p>Đọc đơn kèm dòng hàng ngay từ đầu: nhánh huỷ cần duyệt hết dòng hàng để hoàn kho, mà
     * tách làm hai lần đọc thì nhánh thường phải trả giá cho nhánh hiếm.</p>
     */
    @Override
    @Transactional
    public OrderResponse updateStatus(UUID id, OrderStatusUpdateRequest request) {
        OrderEntity order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found with id: " + id));

        OrderStatus current = order.getStatus();
        OrderStatus next = request.status();

        if (!ALLOWED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(OrderStatus.class)).contains(next)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot change order status from " + current + " to " + next);
        }

        if (next == OrderStatus.CANCELLED) {
            restoreStock(order);
        }
        order.setStatus(next);

        return orderMapper.toResponse(orderRepository.save(order));
    }

    /**
     * Trả hàng về kho khi huỷ đơn.
     *
     * <p>Nạp biến thể theo lô đúng như lúc đặt hàng, không tra từng dòng. Biến thể có thể đã bị
     * xoá khỏi danh mục sau khi khách đặt — lúc đó không còn chỗ nào để cộng trả, bỏ qua dòng đó
     * chứ không chặn việc huỷ đơn.</p>
     */
    private void restoreStock(OrderEntity order) {
        Set<UUID> productIds = order.getItems().stream()
                .map(item -> item.getProduct().getId())
                .collect(Collectors.toSet());

        Map<String, ProductVariantEntity> variants = productVariantRepository
                .findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(
                        variant -> variantKey(variant.getProduct().getId(), variant.getSize()),
                        Function.identity()));

        for (OrderItemEntity item : order.getItems()) {
            ProductVariantEntity variant = variants.get(variantKey(item.getProduct().getId(), item.getSize()));
            if (variant != null) {
                variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOrders(UUID accountId) {
        return orderRepository.existsByAccountId(accountId);
    }

    /**
     * Khoá định danh một dòng tồn kho: một sản phẩm có nhiều size, phải đủ cả cặp mới trỏ đúng
     * một bản ghi. Dùng chung cho cả việc gộp dòng trùng trong giỏ lẫn việc tra biến thể đã nạp.
     */
    private String variantKey(UUID productId, ProductSize size) {
        return productId + "_" + size;
    }

    /**
     * Kiểm tồn kho, trừ kho và chụp lại thông tin hiển thị vào dòng hàng.
     *
     * <p>Sản phẩm và biến thể được truyền vào dưới dạng map đã nạp sẵn chứ không tự đi tra
     * database: hàm này chạy trong vòng lặp, tra ở đây là nhân số câu SQL lên theo số dòng hàng.
     * Entity trong map vẫn do persistence context quản lý nên {@code setStockQuantity} dưới đây
     * vẫn được ghi xuống bình thường, và {@code @Version} vẫn chặn được tranh chấp tồn kho.</p>
     */
    private OrderItemEntity buildItem(OrderItemRequest request, OrderEntity order,
                                      Map<UUID, ProductEntity> products,
                                      Map<String, ProductVariantEntity> variants) {
        ProductEntity product = Optional.ofNullable(products.get(request.productId()))
                .filter(p -> p.getStatus() == null || p.getStatus() == RecordStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Product not available: " + request.productId()));

        ProductVariantEntity variant = Optional
                .ofNullable(variants.get(variantKey(product.getId(), request.size())))
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
