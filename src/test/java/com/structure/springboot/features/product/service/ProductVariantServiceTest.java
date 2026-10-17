package com.structure.springboot.features.product.service;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.order.enums.OrderStatus;
import com.structure.springboot.features.order.repository.OrderItemRepository;
import com.structure.springboot.features.product.dto.request.ProductVariantRequest;
import com.structure.springboot.features.product.dto.request.ProductVariantUpdateRequest;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.entity.ProductVariantEntity;
import com.structure.springboot.features.product.enums.ProductSize;
import com.structure.springboot.features.product.mapper.ProductMapper;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.service.impl.ProductServiceImpl;
import com.structure.springboot.infrastructure.external.cloudinary.ManagedImageService;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tồn kho theo size: thêm, sửa có kiểm phiên bản, xoá an toàn với đơn hàng.
 *
 * <p>Nhãn test để ASCII thuần vì output surefire mã hoá hỏng tiếng Việt có dấu.</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductVariantServiceTest {

    @Mock private ProductRepository products;
    @Mock private CategoryRepository categories;
    @Mock private OrderItemRepository orderItems;
    @Mock private ManagedImageService images;
    private ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        ProductMapper mapper = Mappers.getMapper(ProductMapper.class);
        service = new ProductServiceImpl(products, categories, mapper, orderItems, images);
    }

    private ProductEntity product(RecordStatus status, ProductVariantEntity... variants) {
        ProductEntity product = ProductEntity.builder()
                .name("Vay hoa").slug("vay-hoa").sku("SKU-1").price(BigDecimal.TEN)
                .status(status).build();
        product.setId(UUID.randomUUID());
        product.setVariants(new ArrayList<>(List.of(variants)));
        product.getVariants().forEach(variant -> variant.setProduct(product));
        when(products.findWithLockById(product.getId())).thenReturn(Optional.of(product));
        return product;
    }

    private ProductVariantEntity variant(ProductSize size, int stock, long version) {
        ProductVariantEntity entity = ProductVariantEntity.builder()
                .size(size).stockQuantity(stock).build();
        entity.setId(UUID.randomUUID());
        entity.setVersion(version);
        return entity;
    }

    // ───────────────────────── them variant ─────────────────────────

    @Test
    void addVariantCreatesFirstSizeAndAllowsZeroStock() {
        ProductEntity product = product(RecordStatus.HIDDEN);

        var result = service.addVariant(product.getId(), new ProductVariantRequest(ProductSize.S, 0));

        assertEquals(1, product.getVariants().size());
        assertEquals(ProductSize.S, product.getVariants().get(0).getSize());
        assertEquals(0, product.getVariants().get(0).getStockQuantity());
        // Response phai cong theo variant vua them, FE khong phai goi lai.
        assertEquals(1, result.variants().size());
        assertSame(product, product.getVariants().get(0).getProduct());
    }

    @Test
    void addVariantRejectsDuplicateSize() {
        ProductEntity product = product(RecordStatus.HIDDEN, variant(ProductSize.S, 5, 0));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.addVariant(product.getId(), new ProductVariantRequest(ProductSize.S, 9)));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("S"));
        assertEquals(1, product.getVariants().size());
    }

    @Test
    void addVariantAcceptsSecondDistinctSize() {
        ProductEntity product = product(RecordStatus.ACTIVE, variant(ProductSize.S, 5, 0));

        service.addVariant(product.getId(), new ProductVariantRequest(ProductSize.M, 7));

        assertEquals(2, product.getVariants().size());
    }

    @Test
    void addVariantRejectsDeletedProduct() {
        ProductEntity product = product(RecordStatus.DELETED);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.addVariant(product.getId(), new ProductVariantRequest(ProductSize.S, 1)));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    // ───────────────────────── sua ton kho ─────────────────────────

    @Test
    void updateVariantAppliesStockWhenVersionMatches() {
        ProductVariantEntity target = variant(ProductSize.M, 4, 3);
        ProductEntity product = product(RecordStatus.ACTIVE, target);

        service.updateVariant(product.getId(), target.getId(),
                new ProductVariantUpdateRequest(25, 3L));

        assertEquals(25, target.getStockQuantity());
    }

    @Test
    void updateVariantRejectsStaleVersion() {
        ProductVariantEntity target = variant(ProductSize.M, 4, 3);
        ProductEntity product = product(RecordStatus.ACTIVE, target);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateVariant(product.getId(), target.getId(),
                        new ProductVariantUpdateRequest(25, 2L)));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals(4, target.getStockQuantity()); // khong ghi de
    }

    @Test
    void updateVariantRejectsVariantOfAnotherProduct() {
        ProductEntity product = product(RecordStatus.ACTIVE, variant(ProductSize.S, 2, 0));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateVariant(product.getId(), UUID.randomUUID(),
                        new ProductVariantUpdateRequest(5, 0L)));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updateVariantAllowsZeroToMarkTemporarilyOutOfStock() {
        ProductVariantEntity target = variant(ProductSize.L, 9, 0);
        ProductEntity product = product(RecordStatus.ACTIVE, target);

        service.updateVariant(product.getId(), target.getId(),
                new ProductVariantUpdateRequest(0, 0L));

        assertEquals(0, target.getStockQuantity());
    }

    // ───────────────────────── xoa variant ─────────────────────────

    @Test
    void removeVariantDeletesWhenNoOpenOrderReferencesIt() {
        ProductVariantEntity gone = variant(ProductSize.S, 1, 0);
        ProductEntity product = product(RecordStatus.ACTIVE, gone, variant(ProductSize.M, 2, 0));
        when(orderItems.existsByProductIdAndSizeAndOrderStatusIn(eq(product.getId()),
                eq(ProductSize.S), anyCollection())).thenReturn(false);

        service.removeVariant(product.getId(), gone.getId());

        assertEquals(1, product.getVariants().size());
        assertEquals(ProductSize.M, product.getVariants().get(0).getSize());
    }

    @Test
    void removeVariantRejectsSizeStillInAnOpenOrder() {
        ProductVariantEntity gone = variant(ProductSize.S, 1, 0);
        ProductEntity product = product(RecordStatus.ACTIVE, gone, variant(ProductSize.M, 2, 0));
        when(orderItems.existsByProductIdAndSizeAndOrderStatusIn(eq(product.getId()),
                eq(ProductSize.S), anyCollection())).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.removeVariant(product.getId(), gone.getId()));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals(2, product.getVariants().size());
    }

    @Test
    void removeVariantChecksOnlyUnfinishedOrderStatuses() {
        ProductVariantEntity gone = variant(ProductSize.S, 1, 0);
        ProductEntity product = product(RecordStatus.HIDDEN, gone);
        when(orderItems.existsByProductIdAndSizeAndOrderStatusIn(any(), any(), anyCollection()))
                .thenAnswer(invocation -> {
                    Collection<?> statuses = invocation.getArgument(2);
                    // Don da xong/da huy KHONG duoc tinh: order_items giu ban chup rieng.
                    assertTrue(statuses.contains(OrderStatus.PENDING));
                    assertTrue(statuses.contains(OrderStatus.CONFIRMED));
                    assertTrue(statuses.contains(OrderStatus.SHIPPING));
                    assertEquals(3, statuses.size());
                    return false;
                });

        service.removeVariant(product.getId(), gone.getId());

        assertTrue(product.getVariants().isEmpty());
    }

    @Test
    void removeLastVariantOfActiveProductIsRejectedButHiddenProductCanDropIt() {
        ProductVariantEntity only = variant(ProductSize.S, 1, 0);
        ProductEntity active = product(RecordStatus.ACTIVE, only);
        lenient().when(orderItems.existsByProductIdAndSizeAndOrderStatusIn(any(), any(), anyCollection()))
                .thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.removeVariant(active.getId(), only.getId()));
        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals(1, active.getVariants().size());

        // Cung san pham do nhung da an thi go duoc size cuoi.
        active.setStatus(RecordStatus.HIDDEN);
        service.removeVariant(active.getId(), only.getId());
        assertTrue(active.getVariants().isEmpty());
    }

    @Test
    void removeVariantRequiresVariantId() {
        ProductEntity product = product(RecordStatus.HIDDEN, variant(ProductSize.S, 1, 0));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.removeVariant(product.getId(), null));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verifyNoInteractions(orderItems);
    }

    // ───────────────────────── liet ke ─────────────────────────

    @Test
    void adminVariantListIncludesOutOfStockSizesAndCarriesVersion() {
        ProductEntity product = ProductEntity.builder()
                .name("Vay").slug("vay").sku("SKU-2").price(BigDecimal.ONE)
                .status(RecordStatus.ACTIVE).build();
        product.setId(UUID.randomUUID());
        product.setVariants(new ArrayList<>(List.of(variant(ProductSize.S, 0, 7))));
        product.getVariants().forEach(variant -> variant.setProduct(product));
        when(products.findById(product.getId())).thenReturn(Optional.of(product));

        var result = service.getAdminVariants(product.getId());

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).stockQuantity());
        assertEquals(7L, result.get(0).version()); // FE gui lai o expectedVersion
    }
}
