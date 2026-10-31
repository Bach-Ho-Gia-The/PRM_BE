package com.structure.springboot.features.order.service;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.notification.service.NotificationService;
import com.structure.springboot.features.order.config.OrderProperties;
import com.structure.springboot.features.order.dto.request.OrderItemRequest;
import com.structure.springboot.features.order.dto.request.OrderRequest;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.entity.OrderItemEntity;
import com.structure.springboot.features.order.dto.request.OrderStatusUpdateRequest;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.order.enums.PaymentMethod;
import com.structure.springboot.features.order.mapper.OrderMapper;
import com.structure.springboot.features.order.repository.OrderRepository;
import com.structure.springboot.features.payment.service.PaymentService;
import com.structure.springboot.features.order.service.impl.OrderServiceImpl;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.entity.ProductVariantEntity;
import com.structure.springboot.features.product.enums.ProductSize;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.repository.ProductVariantRepository;
import com.structure.springboot.infrastructure.external.brevo.BrevoMailClient;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import com.structure.springboot.features.order.config.OrderExpiryProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Luật nghiệp vụ của đơn hàng không nằm trong annotation validation.
 *
 * <p>Nhãn test để ASCII thuần vì output surefire mã hoá hỏng tiếng Việt có dấu.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderBusinessRuleTest {

    @Mock private OrderRepository orders;
    @Mock private AccountRepository accounts;
    @Mock private ProductRepository products;
    @Mock private ProductVariantRepository variants;
    @Mock private NotificationService notifications;
    @Mock private BrevoMailClient mail;
    @Mock private PaymentService payments;
    private OrderServiceImpl service;

    private AccountEntity account;
    private ProductEntity product;


    private final OrderExpiryCooldown cooldown = mock(OrderExpiryCooldown.class);
    private final OrderExpiryProperties expiryProperties = new OrderExpiryProperties(
            Duration.ofMinutes(1), Duration.ofSeconds(30), Duration.ofMinutes(5), 100, 5, 24);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<OrderService> provider = mockProvider();

    @SuppressWarnings("unchecked")
    private static ObjectProvider<OrderService> mockProvider() {
        ObjectProvider<OrderService> provider = mock(ObjectProvider.class);
        lenient().when(provider.getObject()).thenReturn(mock(OrderService.class));
        return provider;
    }

    @BeforeEach
    void setUp() {
        OrderMapper mapper = Mappers.getMapper(OrderMapper.class);
        service = new OrderServiceImpl(orders, accounts, products, variants, mapper,
                notifications, mail, new OrderProperties(null, null, null), payments,
                expiryProperties, cooldown, provider);

        account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setEmail("khach@example.com");

        product = ProductEntity.builder()
                .name("Vay hoa").slug("vay-hoa").sku("SKU-1")
                .price(new BigDecimal("100000")).status(RecordStatus.ACTIVE).build();
        product.setId(UUID.randomUUID());
        product.setVariants(new ArrayList<>());
    }

    private void stubCatalog(int stock) {
        ProductVariantEntity variant = ProductVariantEntity.builder()
                .product(product).size(ProductSize.M).stockQuantity(stock).build();
        variant.setId(UUID.randomUUID());
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(products.findByIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(product));
        when(variants.findByProductIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(variant));
        lenient().when(orders.existsByOrderNumber(any())).thenReturn(false);
        lenient().when(orders.saveAndFlush(any(OrderEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    private OrderRequest withLines(OrderItemRequest... lines) {
        return new OrderRequest("Nguyen Van A", "0901234567", "a@b.com",
                "12 Nguyen Hue, Q1", null, PaymentMethod.COD, List.of(lines));
    }

    private OrderItemRequest line(int quantity) {
        return new OrderItemRequest(product.getId(), ProductSize.M, quantity);
    }

    // ───────────── tran so luong khong bi lach qua viec gop dong ─────────────

    /**
     * @Max(999) tren OrderItemRequest chay tren TUNG phan tu client gui len. Gui cung mot
     * (san pham, size) lam 3 dong 999 thi sau khi gop thanh 2.997 — phai chan lai.
     */
    @Test
    void repeatingTheSameLineCannotSneakPastThePerLineCap() {
        stubCatalog(10_000);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createOrder(account.getId(),
                        withLines(line(999), line(999), line(999))));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void exactlyTheCapAfterMergingIsStillAccepted() {
        stubCatalog(10_000);

        var result = service.createOrder(account.getId(), withLines(line(500), line(499)));

        assertEquals(1, result.items().size());
        assertEquals(999, result.items().get(0).quantity());
    }

    @Test
    void duplicateLinesAreMergedIntoOneOrderItem() {
        stubCatalog(100);

        var result = service.createOrder(account.getId(), withLines(line(2), line(3)));

        assertEquals(1, result.items().size());
        assertEquals(5, result.items().get(0).quantity());
        // 5 x 100.000 = 500.000 chua dat nguong freeship 1.000.000 -> cong 30.000
        assertEquals(new BigDecimal("530000"), result.total());
    }

    @Test
    void stockIsCheckedAgainstTheMergedQuantityNotEachLine() {
        stubCatalog(4); // 3 + 3 = 6 > 4

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createOrder(account.getId(), withLines(line(3), line(3))));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
    }

    // ───────────── admin tra don theo ma don ─────────────

    @Test
    void adminCanLookUpAnOrderByTheNumberTheCustomerReadsOnThePhone() {
        OrderEntity entity = OrderEntity.builder()
                .orderNumber("LBVN-2610-A7K2").account(account).status(OrderStatus.CONFIRMED)
                .subtotal(BigDecimal.TEN).total(BigDecimal.TEN).build();
        entity.setId(UUID.randomUUID());
        entity.setItems(new ArrayList<>());
        when(orders.findWithItemsByOrderNumber("LBVN-2610-A7K2")).thenReturn(Optional.of(entity));

        assertEquals("LBVN-2610-A7K2", service.getOrderByNumber("LBVN-2610-A7K2").orderNumber());
    }

    @Test
    void anUnknownOrderNumberIsNotFound() {
        when(orders.findWithItemsByOrderNumber("LBVN-9999-ZZZZ")).thenReturn(Optional.empty());

        assertEquals(ErrorCode.NOT_FOUND, assertThrows(ApiException.class,
                () -> service.getOrderByNumber("LBVN-9999-ZZZZ")).getErrorCode());
    }

    // ───────────── han thanh toan cua don ─────────────

    private OrderRequest withMethod(PaymentMethod method) {
        return new OrderRequest("Nguyen Van A", "0901234567", "a@b.com",
                "12 Nguyen Hue, Q1", null, method, List.of(line(1)));
    }

    /** Lay dung entity ma service da dua xuong save(). */
    private OrderEntity savedOrder() {
        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orders).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    /** COD tra tien luc nhan hang nen KHONG co han. Co han thi don bi tu huy oan. */
    @Test
    void aCodOrderHasNoPaymentDeadline() {
        stubCatalog(10);

        service.createOrder(account.getId(), withMethod(PaymentMethod.COD));

        assertNull(savedOrder().getPaymentDueAt());
    }

    /** Chuyen khoan thi co han, tinh tu luc dat, lay tu cau hinh app.order.payment-timeout. */
    @Test
    void aBankTransferOrderGetsADeadlineFromConfiguration() {
        stubCatalog(10);
        Instant before = Instant.now();

        service.createOrder(account.getId(), withMethod(PaymentMethod.BANK_TRANSFER));

        Instant dueAt = savedOrder().getPaymentDueAt();
        assertNotNull(dueAt);
        // Mac dinh 15m; cho sai so 10s cho thoi gian chay test.
        assertTrue(dueAt.isAfter(before.plus(Duration.ofMinutes(15)).minusSeconds(10)));
        assertTrue(dueAt.isBefore(Instant.now().plus(Duration.ofMinutes(15)).plusSeconds(10)));
    }

    /** Doi cau hinh thi han doi theo — chung minh no DOC TU CONFIG, khong hardcode. */
    @Test
    void theDeadlineComesFromConfigurationNotAHardcodedValue() {
        OrderMapper mapper = Mappers.getMapper(OrderMapper.class);
        OrderServiceImpl threeMinutes = new OrderServiceImpl(orders, accounts, products, variants,
                mapper, notifications, mail,
                new OrderProperties(null, null, Duration.ofMinutes(3)), payments,
                expiryProperties, cooldown, provider);
        stubCatalog(10);
        Instant before = Instant.now();

        threeMinutes.createOrder(account.getId(), withMethod(PaymentMethod.BANK_TRANSFER));

        Instant dueAt = savedOrder().getPaymentDueAt();
        assertNotNull(dueAt);
        assertTrue(dueAt.isAfter(before.plus(Duration.ofMinutes(3)).minusSeconds(10)));
        assertTrue(dueAt.isBefore(Instant.now().plus(Duration.ofMinutes(3)).plusSeconds(10)));
    }

    // ───────────── cua admin huy don cung phai tra kho + dong payment ─────────────

    /**
     * Shop huy don la CUA THU HAI ghi CANCELLED. No phai di qua dung mot cua voi khach huy.
     *
     * <p>Day la test chong loi "mot cua quen mot viec": neu sau nay ai do them duong huy don thu
     * ba (vi du tac vu quet het han) ma chi set status, thi kho mat hang va payment ket PENDING,
     * khong loi nao bao. Test nay khoa hanh vi cua cua admin lai.</p>
     */
    @Test
    void adminCancellingAlsoRestoresStockAndClosesPayment() {
        // Khong goi stubCatalog: test nay khong tao don qua createOrder nen cac stub do la thua,
        // va Mockito strict se bao UnnecessaryStubbing.
        OrderEntity order = OrderEntity.builder()
                .orderNumber("LBVN-2610-ADMN").account(account)
                .status(OrderStatus.PENDING).paymentMethod(PaymentMethod.BANK_TRANSFER)
                .receiverName("Nguyen Van A").phone("0901234567").address("12 Nguyen Hue, Q1")
                .subtotal(new BigDecimal("100000")).shippingFee(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO).total(new BigDecimal("100000")).totalItems(1)
                .build();
        order.setId(UUID.randomUUID());

        ProductVariantEntity stock = ProductVariantEntity.builder()
                .product(product).size(ProductSize.M).stockQuantity(7).build();
        stock.setId(UUID.randomUUID());
        order.getItems().add(OrderItemEntity.builder()
                .order(order).product(product).productName(product.getName())
                .productSlug(product.getSlug()).size(ProductSize.M)
                .unitPrice(new BigDecimal("100000")).quantity(2).lineTotal(new BigDecimal("200000"))
                .build());

        when(orders.findForUpdateWithItemsById(order.getId())).thenReturn(Optional.of(order));
        when(variants.findByProductIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(stock));
        when(orders.saveAndFlush(any(OrderEntity.class))).thenAnswer(call -> call.getArgument(0));

        service.updateStatus(order.getId(), new OrderStatusUpdateRequest(OrderStatus.CANCELLED));

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        // Cua admin cung phai tra kho...
        assertEquals(9, stock.getStockQuantity());   // 7 + 2
        assertEquals(Boolean.TRUE, order.getStockRestored());
        // ...va cung phai dong payment, khong duoc bo qua.
        verify(payments).releaseForOrderCancel(order);
    }
}
