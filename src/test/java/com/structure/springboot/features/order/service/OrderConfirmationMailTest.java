package com.structure.springboot.features.order.service;

import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.notification.service.NotificationService;
import com.structure.springboot.features.order.config.OrderProperties;
import com.structure.springboot.features.order.dto.request.OrderItemRequest;
import com.structure.springboot.features.order.dto.request.OrderRequest;
import com.structure.springboot.features.order.entity.OrderEntity;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mail xác nhận đơn hàng bắn đúng địa chỉ và đúng số liệu.
 *
 * <p>Nhãn test để ASCII thuần vì output surefire mã hoá hỏng tiếng Việt có dấu.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderConfirmationMailTest {

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

    @BeforeEach
    void setUp() {
        OrderMapper mapper = Mappers.getMapper(OrderMapper.class);
        service = new OrderServiceImpl(orders, accounts, products, variants, mapper,
                notifications, mail, new OrderProperties(null, null, null), payments);

        account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setEmail("taikhoan@example.com");

        product = ProductEntity.builder()
                .name("Vay hoa").slug("vay-hoa").sku("SKU-1")
                .price(new BigDecimal("250000")).status(RecordStatus.ACTIVE).build();
        product.setId(UUID.randomUUID());
        product.setVariants(new ArrayList<>());
    }

    private void stubHappyPath(int stock) {
        ProductVariantEntity variant = ProductVariantEntity.builder()
                .product(product).size(ProductSize.M).stockQuantity(stock).build();
        variant.setId(UUID.randomUUID());
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(products.findByIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(product));
        when(variants.findByProductIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(variant));
        when(orders.existsByOrderNumber(any())).thenReturn(false);
        when(orders.save(any(OrderEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    private OrderRequest request(String email) {
        return new OrderRequest("Nguyen Van A", "0901234567", email,
                "12 Nguyen Hue, Q1", null, PaymentMethod.COD,
                List.of(new OrderItemRequest(product.getId(), ProductSize.M, 2)));
    }

    @Test
    void confirmationGoesToTheEmailTypedOnTheOrderForm() {
        stubHappyPath(10);

        service.createOrder(account.getId(), request("nguoinhan@example.com"));

        verify(mail).sendOrderConfirmationEmail(eq("nguoinhan@example.com"), any(),
                eq("Nguyen Van A"), any(BigDecimal.class), eq("12 Nguyen Hue, Q1"));
    }

    /** Email tren don la TUY CHON: thieu thi phai lui ve email tai khoan, khong duoc gui null. */
    @Test
    void confirmationFallsBackToTheAccountEmailWhenTheFormLeftItEmpty() {
        stubHappyPath(10);

        service.createOrder(account.getId(), request(null));

        verify(mail).sendOrderConfirmationEmail(eq("taikhoan@example.com"), any(), any(),
                any(BigDecimal.class), any());
    }

    @Test
    void blankEmailIsTreatedAsMissingNotAsAnAddress() {
        stubHappyPath(10);

        service.createOrder(account.getId(), request("   "));

        verify(mail).sendOrderConfirmationEmail(eq("taikhoan@example.com"), any(), any(),
                any(BigDecimal.class), any());
    }

    /** So tien trong mail phai la TONG da cong ship, khong phai subtotal. */
    @Test
    void mailCarriesTheGrandTotalIncludingShipping() {
        stubHappyPath(10);

        var result = service.createOrder(account.getId(), request("a@b.com"));

        // 2 x 250.000 = 500.000 chua dat nguong freeship 1.000.000 -> cong 30.000
        assertEquals(new BigDecimal("530000"), result.total());
        verify(mail).sendOrderConfirmationEmail(any(), any(), any(),
                eq(new BigDecimal("530000")), any());
    }

    @Test
    void mailCarriesTheSameOrderNumberTheCustomerSees() {
        stubHappyPath(10);

        var result = service.createOrder(account.getId(), request("a@b.com"));

        verify(mail).sendOrderConfirmationEmail(any(), eq(result.orderNumber()), any(),
                any(BigDecimal.class), any());
    }
}
