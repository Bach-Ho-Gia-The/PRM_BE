package com.structure.springboot.features.order.service;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.notification.service.NotificationService;
import com.structure.springboot.features.order.dto.request.OrderCancelRequest;
import com.structure.springboot.features.order.entity.OrderEntity;
import com.structure.springboot.features.order.entity.OrderItemEntity;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.order.mapper.OrderMapper;
import com.structure.springboot.features.order.repository.OrderRepository;
import com.structure.springboot.features.payment.enums.RefundReason;
import com.structure.springboot.features.payment.service.PaymentService;
import com.structure.springboot.features.order.service.impl.OrderServiceImpl;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.entity.ProductVariantEntity;
import com.structure.springboot.features.product.enums.ProductSize;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.repository.ProductVariantRepository;
import com.structure.springboot.features.order.config.OrderProperties;
import com.structure.springboot.infrastructure.external.brevo.BrevoMailClient;
import com.structure.springboot.features.order.config.OrderExpiryProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Khách tự huỷ đơn PENDING của chính mình.
 *
 * <p>Nhãn test để ASCII thuần vì output surefire mã hoá hỏng tiếng Việt có dấu.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderCancelServiceTest {

    @Mock private OrderRepository orders;
    @Mock private AccountRepository accounts;
    @Mock private ProductRepository products;
    @Mock private ProductVariantRepository variants;
    @Mock private NotificationService notifications;
    @Mock private OrderProperties orderProperties;
    @Mock private BrevoMailClient mail;
    @Mock private PaymentService payments;
    private OrderServiceImpl service;

    private final UUID me = UUID.randomUUID();


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
                notifications, mail, orderProperties, payments,
                expiryProperties, cooldown, provider);
    }

    private ProductEntity product() {
        ProductEntity entity = ProductEntity.builder()
                .name("Vay").slug("vay").sku("SKU-1").price(BigDecimal.TEN).build();
        entity.setId(UUID.randomUUID());
        return entity;
    }

    private OrderEntity order(OrderStatus status, OrderItemEntity... items) {
        AccountEntity account = new AccountEntity();
        account.setId(me);
        OrderEntity entity = OrderEntity.builder()
                .orderNumber("LBVN-2610-A7K2").account(account).status(status)
                .subtotal(BigDecimal.TEN).total(BigDecimal.TEN).build();
        entity.setId(UUID.randomUUID());
        entity.setItems(new ArrayList<>(List.of(items)));
        entity.getItems().forEach(item -> item.setOrder(entity));
        return entity;
    }

    private OrderItemEntity item(ProductEntity product, ProductSize size, int quantity) {
        return OrderItemEntity.builder()
                .product(product).productName(product.getName()).size(size)
                .quantity(quantity).unitPrice(BigDecimal.ONE).lineTotal(BigDecimal.ONE).build();
    }

    private ProductVariantEntity variant(ProductEntity product, ProductSize size, int stock) {
        ProductVariantEntity entity = ProductVariantEntity.builder()
                .product(product).size(size).stockQuantity(stock).build();
        entity.setId(UUID.randomUUID());
        return entity;
    }

    private void echoSave() {
        when(orders.saveAndFlush(any(OrderEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void cancellingAPendingOrderFlipsStatusAndStoresTheReason() {
        ProductEntity product = product();
        OrderEntity entity = order(OrderStatus.PENDING, item(product, ProductSize.S, 2));
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me)).thenReturn(Optional.of(entity));
        when(variants.findByProductIdInOrderByIdAsc(anyCollection()))
                .thenReturn(List.of(variant(product, ProductSize.S, 5)));
        echoSave();

        var result = service.cancelMyOrder(me, "LBVN-2610-A7K2", new OrderCancelRequest("Dat nham size"));

        assertEquals(OrderStatus.CANCELLED, result.status());
        assertEquals("Dat nham size", result.cancelReason());
    }

    /** Ca quan trong nhat: huy xong kho phai ve dung so truoc khi dat. */
    @Test
    void cancellingReturnsEveryLineBackToStock() {
        ProductEntity product = product();
        OrderEntity entity = order(OrderStatus.PENDING, item(product, ProductSize.S, 2));
        ProductVariantEntity stock = variant(product, ProductSize.S, 5);
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me)).thenReturn(Optional.of(entity));
        when(variants.findByProductIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(stock));
        echoSave();

        service.cancelMyOrder(me, "LBVN-2610-A7K2", null);

        assertEquals(7, stock.getStockQuantity()); // 5 + 2
    }

    @Test
    void everySizeOfAMultiLineOrderIsRestored() {
        ProductEntity product = product();
        OrderEntity entity = order(OrderStatus.PENDING,
                item(product, ProductSize.S, 1),
                item(product, ProductSize.M, 3),
                item(product, ProductSize.L, 2));
        ProductVariantEntity small = variant(product, ProductSize.S, 0);
        ProductVariantEntity medium = variant(product, ProductSize.M, 10);
        ProductVariantEntity large = variant(product, ProductSize.L, 4);
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me)).thenReturn(Optional.of(entity));
        when(variants.findByProductIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(small, medium, large));
        echoSave();

        service.cancelMyOrder(me, "LBVN-2610-A7K2", null);

        assertEquals(1, small.getStockQuantity());
        assertEquals(13, medium.getStockQuantity());
        assertEquals(6, large.getStockQuantity());
    }

    @Test
    void cancellingWithoutABodyIsAllowedAndLeavesTheReasonEmpty() {
        ProductEntity product = product();
        OrderEntity entity = order(OrderStatus.PENDING, item(product, ProductSize.S, 1));
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me)).thenReturn(Optional.of(entity));
        when(variants.findByProductIdInOrderByIdAsc(anyCollection()))
                .thenReturn(List.of(variant(product, ProductSize.S, 1)));
        echoSave();

        assertNull(service.cancelMyOrder(me, "LBVN-2610-A7K2", null).cancelReason());
    }

    @Test
    void cancellingNotifiesTheCustomer() {
        ProductEntity product = product();
        OrderEntity entity = order(OrderStatus.PENDING, item(product, ProductSize.S, 1));
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me)).thenReturn(Optional.of(entity));
        when(variants.findByProductIdInOrderByIdAsc(anyCollection()))
                .thenReturn(List.of(variant(product, ProductSize.S, 1)));
        echoSave();

        service.cancelMyOrder(me, "LBVN-2610-A7K2", null);

        verify(notifications).notifyOrderStatusChanged(entity);
    }

    @Test
    void cancellingTwiceIsRejected() {
        OrderEntity entity = order(OrderStatus.CANCELLED);
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me)).thenReturn(Optional.of(entity));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.cancelMyOrder(me, "LBVN-2610-A7K2", null));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void confirmedOrderMustGoThroughTheShop() {
        OrderEntity entity = order(OrderStatus.CONFIRMED);
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me)).thenReturn(Optional.of(entity));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.cancelMyOrder(me, "LBVN-2610-A7K2", null));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        org.junit.jupiter.api.Assertions.assertTrue(
                exception.getMessage().contains("contact the shop"));
    }

    @Test
    void completedOrderCannotBeCancelled() {
        OrderEntity entity = order(OrderStatus.COMPLETED);
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me)).thenReturn(Optional.of(entity));

        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class,
                () -> service.cancelMyOrder(me, "LBVN-2610-A7K2", null)).getErrorCode());
    }

    /** Don cua nguoi khac phai la 404 chu khong 403: khong lo rang ma don do co that. */
    @Test
    void someoneElsesOrderIsNotFoundNotForbidden() {
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-XXXX", me)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class,
                () -> service.cancelMyOrder(me, "LBVN-2610-XXXX", null));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    /**
     * Chong hoan kho hai lan khi don da bi danh dau la da hoan.
     *
     * <p>Mo phong tinh huong that: mot duong khac (shop huy, tac vu het han, IPN ve muon) da hoan
     * kho va bat co truoc khi duong nay doc duoc don. Khoa bi quan KHONG cuu duoc ca nay vi khoa chi
     * song trong mot giao dich — day chinh la ly do phai co co o tang du lieu.</p>
     */
    @Test
    void anOrderAlreadyMarkedAsRestoredDoesNotAddStockAgain() {
        ProductEntity product = product();
        OrderEntity entity = order(OrderStatus.PENDING, item(product, ProductSize.S, 2));
        entity.setStockRestored(Boolean.TRUE);
        ProductVariantEntity stock = variant(product, ProductSize.S, 5);
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me)).thenReturn(Optional.of(entity));
        echoSave();

        service.cancelMyOrder(me, "LBVN-2610-A7K2", null);

        assertEquals(5, stock.getStockQuantity()); // KHONG thanh 7
        verify(variants, never()).findByProductIdInOrderByIdAsc(anyCollection());
    }

    /** Huy thanh cong phai bat co, de lan sau khong hoan nua. */
    @Test
    void cancellingMarksStockAsRestored() {
        ProductEntity product = product();
        OrderEntity entity = order(OrderStatus.PENDING, item(product, ProductSize.S, 2));
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me)).thenReturn(Optional.of(entity));
        when(variants.findByProductIdInOrderByIdAsc(anyCollection()))
                .thenReturn(List.of(variant(product, ProductSize.S, 5)));
        echoSave();

        service.cancelMyOrder(me, "LBVN-2610-A7K2", null);

        assertEquals(Boolean.TRUE, entity.getStockRestored());
    }

    /** Don moi tao: co phai la Boolean false, khong duoc null (cot NOT NULL). */
    @Test
    void aFreshOrderStartsWithStockNotRestored() {
        assertEquals(Boolean.FALSE, order(OrderStatus.PENDING).getStockRestored());
    }

    @Test
    void aVariantDeletedAfterTheOrderDoesNotBlockCancelling() {
        ProductEntity product = product();
        OrderEntity entity = order(OrderStatus.PENDING, item(product, ProductSize.S, 2));
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me)).thenReturn(Optional.of(entity));
        when(variants.findByProductIdInOrderByIdAsc(anyCollection())).thenReturn(List.of()); // size da bi go
        echoSave();

        assertEquals(OrderStatus.CANCELLED,
                service.cancelMyOrder(me, "LBVN-2610-A7K2", null).status());
    }

    // ───────────── huy don phai dong luon payment ─────────────

    /**
     * Don bi huy thi ban ghi thanh toan phai duoc dong lai.
     *
     * <p>Khong dong thi don da chet ma payment con PENDING vinh vien — trang thai bat kha thi,
     * va moi phep dem "don dang cho tien" deu sai.</p>
     */
    @Test
    void cancellingAnOrderAlsoClosesItsPayment() {
        ProductEntity product = product();
        OrderEntity entity = order(OrderStatus.PENDING, item(product, ProductSize.S, 2));
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me)).thenReturn(Optional.of(entity));
        when(variants.findByProductIdInOrderByIdAsc(anyCollection()))
                .thenReturn(List.of(variant(product, ProductSize.S, 5)));
        echoSave();

        service.cancelMyOrder(me, "LBVN-2610-A7K2", new OrderCancelRequest("Doi y"));

        // verify tren chinh doi tuong don: dung cai don vua bi huy, khong phai mot ban sao.
        verify(payments).releaseForOrderCancel(entity);
    }

    /**
     * Don da tra tien van duoc huy (luat 6), nhung KHONG duoc dong payment thanh CANCELLED —
     * viec do la cua chinh PaymentService. O day chi kiem OrderService khong tu y ghi de.
     */
    @Test
    void cancellingAPaidOrderStillGoesThroughTheSameSingleDoor() {
        ProductEntity product = product();
        OrderEntity entity = order(OrderStatus.PENDING, item(product, ProductSize.S, 2));
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me)).thenReturn(Optional.of(entity));
        when(variants.findByProductIdInOrderByIdAsc(anyCollection()))
                .thenReturn(List.of(variant(product, ProductSize.S, 5)));
        echoSave();

        var result = service.cancelMyOrder(me, "LBVN-2610-A7K2", null);

        assertEquals(OrderStatus.CANCELLED, result.status());
        // Van di qua dung mot cua du don da tra tien hay chua: quyet dinh PAID co bi dong hay
        // khong nam trong PaymentService, khong nam o day.
        verify(payments).releaseForOrderCancel(entity);
        // Va hang van phai tra kho — huy don da tra tien thi hang ve kho, tien thi cho hoan.
        assertEquals(Boolean.TRUE, entity.getStockRestored());
    }

    /**
     * HUY DON DA TRA TIEN PHAI SINH KHOAN HOAN — va phai la ly do ORDER_CANCELLED.
     *
     * <p>Test nay lap mot lo hong that: truoc do chi assert {@code releaseForOrderCancel}. Neu ai xoa
     * dong {@code requestRefundIfPaid} trong {@code markCancelled} thi ca bo test van xanh, trong khi
     * don da tra tien bi huy se KHONG co khoan hoan nao — mat tien khach ma khong co loi nao bao.</p>
     */
    @Test
    void cancellingAPaidOrderRecordsARefundWithTheCancelReason() {
        ProductEntity product = product();
        OrderEntity entity = order(OrderStatus.PENDING, item(product, ProductSize.S, 1));
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me))
                .thenReturn(Optional.of(entity));
        when(variants.findByProductIdInOrderByIdAsc(anyCollection()))
                .thenReturn(List.of(variant(product, ProductSize.S, 5)));
        echoSave();

        service.cancelMyOrder(me, "LBVN-2610-A7K2", null);

        verify(payments).requestRefundIfPaid(entity, RefundReason.ORDER_CANCELLED);
    }

    /** Don chua tra tien thi van goi — ham do tu bo qua. Don gian hon la re nhanh o day. */
    @Test
    void cancellingAnUnpaidOrderStillCallsTheRefundHook() {
        ProductEntity product = product();
        OrderEntity entity = order(OrderStatus.PENDING, item(product, ProductSize.M, 1));
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me))
                .thenReturn(Optional.of(entity));
        when(variants.findByProductIdInOrderByIdAsc(anyCollection()))
                .thenReturn(List.of(variant(product, ProductSize.M, 3)));
        echoSave();

        service.cancelMyOrder(me, "LBVN-2610-A7K2", null);

        // Chinh viec "luon goi" moi la thu duoc kiem: quyet dinh co hoan hay khong nam trong
        // PaymentService, khong phai trong OrderService.
        verify(payments).requestRefundIfPaid(entity, RefundReason.ORDER_CANCELLED);
    }

    /** Don khong huy duoc thi khong duoc dong payment. */
    @Test
    void aRejectedCancellationNeverTouchesThePayment() {
        ProductEntity product = product();
        OrderEntity entity = order(OrderStatus.CONFIRMED, item(product, ProductSize.S, 2));
        when(orders.findForUpdateByOrderNumberAndAccountId("LBVN-2610-A7K2", me)).thenReturn(Optional.of(entity));

        assertThrows(ApiException.class,
                () -> service.cancelMyOrder(me, "LBVN-2610-A7K2", null));

        verify(payments, never()).releaseForOrderCancel(any());
    }
}
