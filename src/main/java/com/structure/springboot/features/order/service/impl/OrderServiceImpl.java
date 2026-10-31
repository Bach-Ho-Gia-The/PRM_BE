package com.structure.springboot.features.order.service.impl;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.order.config.OrderExpiryProperties;
import com.structure.springboot.features.order.config.OrderProperties;
import com.structure.springboot.features.order.dto.request.OrderItemRequest;
import com.structure.springboot.features.order.dto.request.OrderCancelRequest;
import com.structure.springboot.features.order.dto.request.OrderRequest;
import com.structure.springboot.features.order.dto.request.OrderStatusUpdateRequest;
import com.structure.springboot.features.order.dto.response.OrderExpirySummary;
import com.structure.springboot.features.order.dto.response.OrderResponse;
import com.structure.springboot.features.order.dto.response.OrderSummaryResponse;
import com.structure.springboot.features.notification.service.NotificationService;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.infrastructure.external.brevo.BrevoMailClient;
import com.structure.springboot.features.order.entity.OrderItemEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.order.enums.PaymentMethod;
import com.structure.springboot.features.order.mapper.OrderMapper;
import com.structure.springboot.features.order.repository.OrderRepository;
import com.structure.springboot.features.order.service.OrderExpiryCooldown;
import com.structure.springboot.features.order.service.OrderService;
import com.structure.springboot.features.order.service.TransactionRetry;
import com.structure.springboot.features.order.specification.OrderSpecifications;
import com.structure.springboot.features.payment.dto.response.VnpayQueryResult;
import com.structure.springboot.features.payment.enums.RefundReason;
import com.structure.springboot.features.payment.enums.VnpayQueryVerdict;
import com.structure.springboot.features.payment.service.PaymentService;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.entity.ProductVariantEntity;
import com.structure.springboot.features.product.enums.ProductSize;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
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
@Slf4j
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

    /**
     * Lý do huỷ ghi lên đơn khi hệ thống tự huỷ vì quá hạn.
     *
     * <p>Khác {@code null} (shop huỷ) và khác lý do khách tự nhập: khách đọc đơn của mình phải phân
     * biệt được "tôi huỷ" với "hệ thống huỷ vì tôi không thanh toán".</p>
     */
    private static final String EXPIRY_CANCEL_REASON = "Quá hạn thanh toán";

    private final SecureRandom random = new SecureRandom();

    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderMapper orderMapper;
    private final NotificationService notificationService;
    private final BrevoMailClient brevoMailClient;
    private final OrderProperties orderProperties;
    private final PaymentService paymentService;
    private final OrderExpiryProperties expiryProperties;
    private final OrderExpiryCooldown cooldown;

    /**
     * Chính bean này, lấy từ container.
     *
     * <p>Cần để gọi {@link #settleExpiredOrder} <b>qua proxy</b> — gọi thẳng tên hàm trong cùng đối
     * tượng thì {@code @Transactional} bị bỏ qua và không có giao dịch nào được mở. Dùng
     * {@link ObjectProvider} chứ không inject {@code OrderService} trực tiếp để tránh vòng phụ thuộc
     * lúc khởi tạo; {@code getObject()} chỉ được gọi lúc chạy, khi bean đã sẵn sàng.</p>
     */
    private final ObjectProvider<OrderService> self;

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
        // Bản CÓ KHOÁ: đọc số tồn mới nhất và giữ dòng tới lúc commit, nên khách tới sau chờ rồi
        // thấy số đã trừ và bị báo hết hàng ngay — thay vì bị đá ra ở bước commit sau khi đã đi
        // hết luồng đặt hàng.
        Map<String, ProductVariantEntity> variants = productVariantRepository
                .findByProductIdInOrderByIdAsc(productIds).stream()
                .collect(Collectors.toMap(
                        variant -> variantKey(variant.getProduct().getId(), variant.getSize()),
                        Function.identity()));

        BigDecimal subtotal = BigDecimal.ZERO;
        int totalItems = 0;

        for (OrderItemRequest line : lines) {
            // Kiểm LẠI trần sau khi gộp: @Max(999) trên OrderItemRequest chạy trên từng phần tử
            // client gửi lên, nên gửi cùng một (sản phẩm, size) làm 3 dòng 999 là lách được thành
            // 2.997. Giá trị phải khớp @Max của OrderItemRequest.quantity.
            if (line.quantity() > 999) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Quantity must not exceed 999 per product and size");
            }
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
        // COD trả tiền lúc nhận hàng nên không có hạn; chuyển khoản thì có, và mốc tính từ đây.
        // Gán đúng một lần, không có đường nào sửa về sau — đó là cơ chế chống tự gia hạn.
        order.setPaymentDueAt(resolvePaymentDueAt(request.paymentMethod()));

        // saveAndFlush chứ KHÔNG phải save: @CreationTimestamp / @UpdateTimestamp chỉ được
        // Hibernate sinh ra lúc flush. save() mới chỉ persist vào persistence context, nên map
        // ngay sau đó sẽ trả createdAt = null và updatedAt = null — đúng lỗi từng thấy trên
        // Swagger. Các service khác trong dự án (product, profile, collection) cũng dùng
        // saveAndFlush vì cùng lý do; riêng order từng lệch.
        OrderEntity saved = orderRepository.saveAndFlush(order);

        // Ghi nhận lần thử thanh toán đầu tiên, TRONG CÙNG giao dịch với việc tạo đơn: hoặc cả hai
        // cùng ra đời, hoặc cùng không. Tách ra ngoài thì có khoảng thời gian đơn tồn tại mà không
        // có bản ghi thanh toán nào — đơn COD sẽ không có gì để shop bấm "đã thu tiền".
        // Áp cho cả COD: đơn COD vẫn có payment, chỉ khác là không có hạn thanh toán.
        paymentService.createFirstAttempt(saved);

        sendConfirmationAfterCommit(
                StringUtils.hasText(saved.getEmail()) ? saved.getEmail() : account.getEmail(),
                saved.getOrderNumber(), saved.getReceiverName(), saved.getTotal(), saved.getAddress());

        return toResponse(saved);
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
                .map(this::toResponse)
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
    public OrderResponse getOrderByNumber(String orderNumber) {
        return toResponse(orderRepository.findWithItemsByOrderNumber(orderNumber)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Order not found with number: " + orderNumber)));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        return toResponse(orderRepository.findWithItemsById(id)
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
        // Bản CÓ KHOÁ: shop huỷ đơn có thể chạy song song với khách huỷ, tác vụ quét hết hạn
        // hoặc IPN báo kết quả. Không khoá thì hai đường cùng đọc PENDING và cùng cộng kho.
        OrderEntity order = orderRepository.findForUpdateWithItemsById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found with id: " + id));

        OrderStatus current = order.getStatus();
        OrderStatus next = request.status();

        if (!ALLOWED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(OrderStatus.class)).contains(next)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot change order status from " + current + " to " + next);
        }

        // Huỷ đơn phải đi qua markCancelled: nó làm đủ trả kho + đóng payment trong cùng giao dịch.
        // Các chuyển trạng thái khác không đụng tới kho nên chỉ cần ghi trạng thái.
        if (next == OrderStatus.CANCELLED) {
            return markCancelled(order, null);
        }

        order.setStatus(next);
        // saveAndFlush: cùng lý do như createOrder — không flush thì updatedAt trong response
        // vẫn là giá trị CŨ đọc từ DB, không phải thời điểm vừa ghi.
        OrderResponse response = toResponse(orderRepository.saveAndFlush(order));

        // Sau khi đã ghi trạng thái: thông báo hỏng không được kéo đổ việc đổi trạng thái,
        // service bên kia tự nuốt lỗi và chỉ ghi log.
        notificationService.notifyOrderStatusChanged(order);
        return response;
    }

    @Override
    @Transactional
    public OrderResponse cancelMyOrder(UUID accountId, String orderNumber,
                                       OrderCancelRequest request) {
        // Bản CÓ KHOÁ — cùng lý do như updateStatus.
        OrderEntity order = orderRepository.findForUpdateByOrderNumberAndAccountId(orderNumber, accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Order not found with number: " + orderNumber));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ApiException(ErrorCode.CONFLICT, "Order is already cancelled");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Order is already confirmed; contact the shop to cancel");
        }

        // Dùng chung đúng một cửa với admin huỷ và (sau này) tác vụ quét hết hạn: ba bản huỷ khác
        // nhau là mầm lệch số liệu kho và payment kẹt PENDING.
        return markCancelled(order, request == null ? null : request.reason());
    }

    /**
     * Quét đơn quá hạn thanh toán — xem javadoc ở {@link OrderService#expireOverdueOrders}.
     *
     * <h2>Vì sao hàm này KHÔNG {@code @Transactional}</h2>
     *
     * <p>Cố ý: nó gọi VNPay cho từng đơn. Bọc cả hàm trong một giao dịch nghĩa là một giao dịch
     * database sống suốt thời gian chờ mạng — với một trăm đơn, mỗi đơn tới mười giây, đó là một
     * giao dịch mở hàng phút và giữ khoá trên mọi dòng nó đã chạm. Ngoài ra giao dịch lồng vào trong
     * cũng chẳng có tác dụng gì: mỗi đơn đã có giao dịch riêng ở chặng chốt, do phương thức public
     * của chính bean này gọi qua proxy.</p>
     *
     * <h2>Vì sao gọi {@code self.settleExpiredOrder} chứ không gọi thẳng tên hàm</h2>
     *
     * <p>{@code @Transactional} của Spring hoạt động bằng proxy: gọi một phương thức trong cùng một
     * đối tượng thì <b>không</b> đi qua proxy, nên annotation bị bỏ qua và không có giao dịch nào
     * được mở. Dùng {@code ObjectProvider<OrderService>} để lấy chính bean từ container rồi gọi qua
     * đó là cách duy nhất giữ được giao dịch cho từng đơn mà không phải tách sang một bean khác — mà
     * tách sang bean khác thì lại sinh vòng phụ thuộc, vì bean mới cần chính service này để huỷ đơn.
     * </p>
     */
    @Override
    public OrderExpirySummary expireOverdueOrders(Instant now) {
        // Chặn cận dưới: đơn quá hạn quá lâu đã được xử lý ở những lượt rất cũ. Sau khi ứng dụng
        // khởi động lại, bộ đếm trong bộ nhớ mất sạch — không có cận dưới thì mọi đơn bị bỏ quên từ
        // nhiều tháng trước sẽ ùa vào lượt quét đầu tiên.
        Instant earliestDueAt = now.minus(Duration.ofHours(expiryProperties.maxAgeHours()));

        Pageable page = PageRequest.of(0, expiryProperties.batchSize(),
                Sort.by(Sort.Direction.ASC, "paymentDueAt"));

        List<OrderEntity> candidates = orderRepository
                .findByStatusAndPaymentDueAtLessThanEqualAndPaymentDueAtGreaterThanEqual(
                        OrderStatus.PENDING, now, earliestDueAt, page);

        if (candidates.isEmpty()) {
            return OrderExpirySummary.empty();
        }

        int cancelled = 0;
        int paid = 0;
        int unclear = 0;

        for (OrderEntity candidate : candidates) {
            // Chặng 2: hỏi VNPay, KHÔNG giữ khoá. Bộ đếm quyết định đơn này đã tới lượt hỏi lại chưa.
            if (!cooldown.tryAcquire(candidate.getId(), now)) {
                unclear++;
                continue;
            }

            VnpayQueryVerdict verdict = ask(paymentService.queryVnpayForOrder(candidate));

            if (verdict == VnpayQueryVerdict.PAID) {
                cooldown.forget(candidate.getId());
                paid++;
                continue;
            }

            // Không rõ thì không đụng gì. Đây là nhánh quan trọng nhất của cả hàm: UNKNOWN không
            // phải FAILED, và mọi thứ không chắc chắn đều rơi vào đây.
            if (!verdict.allowsCancel()) {
                if (cooldown.isExhausted(candidate.getId())) {
                    log.warn("Order {} still unclear after {} VNPay queries — giving up, check manually",
                            candidate.getOrderNumber(), cooldown.failures(candidate.getId()));
                }
                unclear++;
                continue;
            }

            // Chặng 3: chốt trong một giao dịch ngắn, có khoá, và KIỂM LẠI (xem settleExpiredOrder).
            // Bọc retry: chặng này khoá dòng đơn + nhiều dòng biến thể, nên có thể thua deadlock.
            // TransactionRetry gọi settleExpiredOrder QUA proxy nên mỗi lần thử là một giao dịch mới
            // — bắt buộc, vì không thể thử lại trong một giao dịch đã bị database huỷ.
            boolean settled = TransactionRetry.execute(
                    "settle expired order " + candidate.getOrderNumber(),
                    () -> self.getObject().settleExpiredOrder(candidate.getId(), verdict, now));
            if (settled) {
                cooldown.forget(candidate.getId());
                cancelled++;
            } else {
                // Đơn đã đổi trạng thái trong lúc chờ VNPay (thường là IPN vừa xác nhận thanh
                // toán), hoặc đã có payment PAID. Không huỷ, và không còn gì phải hỏi lại.
                cooldown.forget(candidate.getId());
                paid++;
            }
        }

        return new OrderExpirySummary(candidates.size(), cancelled, paid, unclear);
    }

    /**
     * Chặng chốt: nạp lại đơn <b>có khoá</b>, kiểm lại lần cuối, rồi huỷ nếu còn đủ điều kiện.
     *
     * <p>Chạy trong <b>một giao dịch riêng</b> cho mỗi đơn. Giao dịch chỉ sống trong khoảng đọc–ghi
     * ngắn, không bao gồm câu hỏi VNPay (đã xong ở chặng trước).</p>
     *
     * <p><b>Ba điều kiện kiểm lại, cả ba đều cần:</b></p>
     * <ol>
     *   <li>Đơn còn tồn tại — có thể đã bị xoá.</li>
     *   <li>Đơn còn {@code PENDING} — giữa chặng 2 và chặng 3, khách có thể đã huỷ.</li>
     *   <li>Đơn <b>chưa có payment {@code PAID}</b> — đây là lớp chặn cuối. VNPay có thể trả lời
     *       "thất bại" cho lần thử mới nhất trong khi lần thử trước đã thu được tiền (khách thử hai
     *       lần). Tin câu trả lời của cổng mà bỏ qua dữ liệu của chính mình là huỷ đơn đã trả tiền.
     *       </li>
     * </ol>
     *
     * @param verdict lý do huỷ, chỉ để ghi log — quyết định huỷ hay không đã chốt ở chặng 2
     * @return {@code true} nếu lần này thực sự huỷ đơn
     */
    @Override
    @Transactional
    public boolean settleExpiredOrder(UUID orderId, VnpayQueryVerdict verdict, Instant now) {
        OrderEntity order = orderRepository.findForUpdateWithItemsById(orderId).orElse(null);

        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return false;
        }

        // Kiểm "đã trả tiền" bằng PAYMENT, không bằng orders.paidAt: paidAt là trường cache trên
        // đơn, có thể null dù payment đã PAID. Dựa vào nó là quyết định huỷ dựa trên dữ liệu phụ.
        if (paymentService.hasPaidPayment(order.getId())) {
            log.warn("Order {} has a PAID payment — not expiring despite VNPay verdict {}",
                    order.getOrderNumber(), verdict);
            return false;
        }

        log.info("Order {} expired (VNPay verdict {}), cancelling and restoring stock",
                order.getOrderNumber(), verdict);
        markExpired(order);
        return true;
    }

    /** Đọc verdict mà không ném: service đã hứa không ném, nhưng một tầng nữa không thừa. */
    private VnpayQueryVerdict ask(VnpayQueryResult result) {
        return result == null || result.verdict() == null
                ? VnpayQueryVerdict.UNKNOWN
                : result.verdict();
    }

    /**
     * Map đơn sang DTO, kèm thời điểm đã trả tiền lấy từ feature payment.
     *
     * <p>Gom về một chỗ vì có nhiều đường trả {@code OrderResponse}; để mỗi nơi tự gọi mapper thì
     * sớm muộn có một đường quên truyền {@code paidAt} và trả {@code null} sai sự thật.</p>
     *
     * <p>Đi qua {@code PaymentService} chứ <b>không</b> đọc thẳng {@code PaymentRepository}: tầng
     * nghiệp vụ của một feature không được chạm repository của feature khác.</p>
     */
    private OrderResponse toResponse(OrderEntity order) {
        return orderMapper.toResponse(order, paymentService.findPaidAtForOrder(order.getId()));
    }

    /**
     * <b>Cửa DUY NHẤT ghi trạng thái {@code CANCELLED}.</b>
     *
     * <p>Bốn việc phải xảy ra cùng nhau, trong cùng một giao dịch: trả hàng về kho, đổi trạng thái
     * đơn, đóng bản ghi thanh toán, báo cho shop. Tách rời nhau là mầm của những lỗi không thể phát
     * hiện bằng mắt trên dữ liệu:</p>
     * <ul>
     *   <li>Quên {@link #restoreStock} ⇒ hàng biến mất khỏi kho vĩnh viễn, không lỗi nào báo.</li>
     *   <li>Quên {@code releaseForOrderCancel} ⇒ đơn chết mà payment kẹt {@code PENDING}.</li>
     * </ul>
     *
     * <p><b>Vì sao phải gom ngay bây giờ:</b> trước đây hai cửa ({@code updateStatus} nhánh huỷ và
     * {@code cancelMyOrder}) tự làm lấy. Tác vụ quét hết hạn sắp tới là cửa thứ ba — mỗi cửa tự
     * làm là mỗi lần có cơ hội quên một việc. Gom lại thì cửa thứ ba chỉ việc gọi hàm này.</p>
     *
     * @param cancelReason lý do huỷ; {@code null} với đơn do shop huỷ (giữ nguyên hành vi cũ)
     */
    private OrderResponse markCancelled(OrderEntity order, String cancelReason) {
        return closeAndRestore(order, cancelReason, false);
    }

    /**
     * Cửa huỷ thứ ba: <b>đơn quá hạn thanh toán</b>. Khác {@link #markCancelled} đúng một điểm —
     * bản ghi thanh toán đóng bằng {@code EXPIRED} thay vì {@code CANCELLED}.
     *
     * <p><b>Vì sao phải khác:</b> "khách đổi ý" và "quá hạn" là hai chuyện hoàn toàn khác nhau khi
     * đối soát với VNPay và khi đọc lịch sử của khách. Đóng payment thành {@code CANCELLED} cho cả
     * hai thì mất luôn khả năng phân biệt, và không có cách nào lấy lại — thông tin bị ghi đè chứ
     * không bị thiếu.</p>
     *
     * <p>Vẫn đi qua <b>cùng một hàm</b> với hai cửa kia: hoàn kho, đổi trạng thái, sinh khoản hoàn,
     * báo khách — bốn việc đó phải xảy ra y hệt nhau. Chỉ trạng thái đóng payment là tham số.</p>
     */
    private OrderResponse markExpired(OrderEntity order) {
        return closeAndRestore(order, EXPIRY_CANCEL_REASON, true);
    }

    /**
     * Phần chung của mọi đường huỷ đơn.
     *
     * <p><b>Cửa duy nhất ghi {@code CANCELLED}</b> — ba lối vào (shop huỷ, khách huỷ, hết hạn) khác
     * nhau đúng một tham số. Tách riêng như vậy là để không có đường nào quên một trong bốn việc:
     * trả hàng về kho, đổi trạng thái, đóng payment, sinh khoản hoàn nếu đã thu tiền.</p>
     *
     * @param expired {@code true} nếu huỷ vì quá hạn thanh toán — đóng payment bằng {@code EXPIRED};
     *                {@code false} nếu huỷ do người (khách hoặc shop) — đóng bằng {@code CANCELLED}
     */
    private OrderResponse closeAndRestore(OrderEntity order, String cancelReason, boolean expired) {
        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(cancelReason);
        // Cùng giao dịch: đơn huỷ mà payment không đóng được thì cả hai cùng rollback.
        // expireForOrder đã có sẵn từ CK-2 và chưa ai gọi — đây chính là chỗ dùng nó.
        if (expired) {
            paymentService.expireForOrder(order);
        } else {
            paymentService.releaseForOrderCancel(order);
        }
        // Đơn đã trả tiền thì phải có khoản hoàn — luật 7: huỷ đơn khác với đã hoàn tiền.
        // Hàm tự bỏ qua khi đơn chưa từng được trả tiền, nên không cần rẽ nhánh ở đây.
        paymentService.requestRefundIfPaid(order, RefundReason.ORDER_CANCELLED);

        OrderResponse response = toResponse(orderRepository.saveAndFlush(order));

        // Báo shop SAU khi đã ghi trạng thái, và không để thông báo hỏng kéo đổ việc huỷ đơn:
        // notificationService tự nuốt lỗi và chỉ ghi log (cùng quy ước với updateStatus).
        notificationService.notifyOrderStatusChanged(order);
        return response;
    }

    /**
     * Trả hàng về kho khi huỷ đơn — <b>tối đa một lần cho mỗi đơn</b>.
     *
     * <p>Nạp biến thể theo lô đúng như lúc đặt hàng, không tra từng dòng. Biến thể có thể đã bị
     * xoá khỏi danh mục sau khi khách đặt — lúc đó không còn chỗ nào để cộng trả, bỏ qua dòng đó
     * chứ không chặn việc huỷ đơn.</p>
     *
     * <h2>Hai lớp chặn cộng kho hai lần</h2>
     * <ol>
     *   <li><b>Khoá bi quan trên đơn</b> ({@code findForUpdateWithItemsById} /
     *       {@code findForUpdateByOrderNumberAndAccountId}) — chặn hai đường <b>đang chạy đồng
     *       thời</b>.</li>
     *   <li><b>Cờ {@link OrderEntity#getStockRestored()}</b> — chặn đường tới <b>sau</b>, kể cả khi
     *       giao dịch trước đã commit và khoá đã nhả. Cờ này một chiều: bật rồi không tắt.</li>
     * </ol>
     *
     * <p>Lớp thứ ba là khoá bi quan trên {@code product_variants} — không phải để chặn cộng hai
     * lần mà để chống mất cập nhật khi cộng số lượng.</p>
     *
     * <p><b>Hàm này chạy TRONG cùng giao dịch với việc đổi trạng thái đơn</b>, cố ý không dời sang
     * {@code afterCommit}: dời ra ngoài thì hai việc không còn nguyên tử (đơn đã huỷ mà cộng kho
     * lỗi = huỷ đơn nhưng mất hàng), và khoá bi quan cũng đã nhả nên lớp chặn thứ nhất mất tác
     * dụng. {@code afterCommit} chỉ dành cho việc nằm ngoài database, như gửi mail.</p>
     *
     * @return {@code false} nếu đơn này đã hoàn kho từ trước (không làm gì), {@code true} nếu lần
     *         này thực sự cộng kho
     */
    private boolean restoreStock(OrderEntity order) {
        if (Boolean.TRUE.equals(order.getStockRestored())) {
            return false;
        }

        // Đánh dấu TRƯỚC khi cộng. Cùng một giao dịch nên hoặc cả hai cùng commit, hoặc cả hai
        // cùng rollback — nhưng đặt trước để đọc code là thấy ngay thứ tự, và để nếu bước cộng
        // ném lỗi thì cờ cũng không được lưu lại.
        order.setStockRestored(Boolean.TRUE);

        Set<UUID> productIds = order.getItems().stream()
                .map(item -> item.getProduct().getId())
                .collect(Collectors.toSet());

        Map<String, ProductVariantEntity> variants = productVariantRepository
                .findByProductIdInOrderByIdAsc(productIds).stream()
                .collect(Collectors.toMap(
                        variant -> variantKey(variant.getProduct().getId(), variant.getSize()),
                        Function.identity()));

        for (OrderItemEntity item : order.getItems()) {
            ProductVariantEntity variant = variants.get(variantKey(item.getProduct().getId(), item.getSize()));
            if (variant != null) {
                variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
            }
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOrders(UUID accountId) {
        return orderRepository.existsByAccountId(accountId);
    }

    /**
     * Hẹn gửi mail xác nhận SAU KHI giao dịch commit thành công.
     *
     * <p><b>Vì sao không gọi thẳng:</b> giao dịch vẫn có thể hỏng <b>đúng lúc commit</b> — sau khi
     * thân hàm đăng ký đã chạy xong — ví dụ lỗi ràng buộc chỉ phát hiện muộn lúc flush. Gọi thẳng
     * thì đơn bị huỷ bỏ mà khách vẫn nhận được mail xác nhận cho một đơn không tồn tại.</p>
     *
     * <p>(Chú thích cũ kể cảnh hai khách cùng mua món cuối. Từ khi luồng đặt hàng khoá bi quan dòng
     * tồn kho, cảnh đó không còn: người tới sau chờ rồi bị báo hết hàng ngay, chứ không bị đá ra ở
     * bước commit. Lý do giữ {@code afterCommit} vẫn đúng, chỉ ví dụ cũ là không còn.)</p>
     *
     * <p><b>Vì sao đăng ký ở đây chứ không trong {@code BrevoMailClient}:</b> {@code @Async} đi
     * qua proxy, mà gọi từ chính bên trong bean đó thì proxy bị bỏ qua và mail sẽ chạy đồng bộ,
     * chặn luôn response. Gọi từ bean này sang bean kia thì {@code @Async} mới có tác dụng.</p>
     *
     * <p>Nhận giá trị rời chứ không nhận {@code OrderEntity}: lúc callback chạy thì phiên
     * Hibernate đã đóng, chạm vào quan hệ lazy là {@code LazyInitializationException}.</p>
     */
    private void sendConfirmationAfterCommit(String toEmail, String orderNumber,
                                             String receiverName, BigDecimal total, String address) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Không nằm trong giao dịch nào (chỉ xảy ra ở unit test): gửi luôn.
            brevoMailClient.sendOrderConfirmationEmail(toEmail, orderNumber, receiverName, total, address);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                brevoMailClient.sendOrderConfirmationEmail(
                        toEmail, orderNumber, receiverName, total, address);
            }
        });
    }

    /**
     * Khoá định danh một dòng tồn kho: một sản phẩm có nhiều size, phải đủ cả cặp mới trỏ đúng một
     * bản ghi. Dùng chung cho việc gộp dòng trùng trong giỏ và việc tra biến thể đã nạp.
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
                .filter(ProductEntity::isVisible)
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
     * Hạn thanh toán của đơn: chuyển khoản thì có, COD thì không.
     *
     * <p>Mốc tính là <b>lúc đơn được tạo</b>. Khách đặt lúc 10:00 rồi 10:12 mới mở link thì vẫn chỉ
     * còn tới 10:15 — cố ý, vì hàng đã bị giữ từ 10:00.</p>
     *
     * <p>Thời lượng đọc từ {@code app.order.payment-timeout} ({@code ORDER_PAYMENT_TIMEOUT}) do
     * {@link OrderProperties} quản, <b>không</b> đọc từ cấu hình VNPay: hạn thanh toán là luật của
     * đơn hàng, không phải của cổng thanh toán nào.</p>
     */
    private Instant resolvePaymentDueAt(PaymentMethod paymentMethod) {
        if (paymentMethod != PaymentMethod.BANK_TRANSFER) {
            return null;
        }
        return Instant.now().plus(orderProperties.paymentTimeout());
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
