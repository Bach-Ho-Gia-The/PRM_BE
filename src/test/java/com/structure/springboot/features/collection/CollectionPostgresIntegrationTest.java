package com.structure.springboot.features.collection;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.collection.dto.request.CollectionCreateRequest;
import com.structure.springboot.features.collection.dto.request.CollectionProductsRequest;
import com.structure.springboot.features.collection.dto.request.CollectionVersionRequest;
import com.structure.springboot.features.collection.repository.CollectionProductRepository;
import com.structure.springboot.features.collection.repository.CollectionRepository;
import com.structure.springboot.features.collection.service.CollectionService;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chạy tự chọn với PostgreSQL test riêng: RUN_COLLECTION_DB_TESTS=true cùng DB_HOST/PORT/NAME/
 * USERNAME/PASSWORD. KHÔNG dùng DB thật/production: test tạo Product/Collection và cập nhật schema.
 * Không có PostgreSQL cấu hình rõ ràng thì bỏ qua; unit tests vẫn chạy trong CI không Docker.
 */
@EnabledIfEnvironmentVariable(named = "RUN_COLLECTION_DB_TESTS", matches = "true")
@SpringBootTest
@TestPropertySource(properties = {
        "app.data-init.enabled=false",
        "spring.docker.compose.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration"
})
class CollectionPostgresIntegrationTest {

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CollectionRepository collectionRepository;
    @Autowired private CollectionProductRepository linkRepository;
    @Autowired private CollectionService collectionService;
    @Autowired private WebApplicationContext webContext;

    @Test
    void versionAndLinksStayConsistentAcrossTransactions() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CategoryEntity category = categoryRepository.save(CategoryEntity.builder()
                .categoryName("Collection test " + suffix)
                .slug("collection-test-" + suffix)
                .status(RecordStatus.ACTIVE)
                .build());
        ProductEntity inactiveLater = createProduct(category, suffix, "a", RecordStatus.ACTIVE);
        ProductEntity removedLater = createProduct(category, suffix, "b", RecordStatus.ACTIVE);
        ProductEntity newlyAdded = createProduct(category, suffix, "c", RecordStatus.ACTIVE);
        ProductEntity cannotAdd = createProduct(category, suffix, "d", RecordStatus.HIDDEN);

        var created = collectionService.createCollection(new CollectionCreateRequest(
                "Manual test " + suffix, "manual-test-" + suffix, null,
                List.of(inactiveLater.getId(), removedLater.getId())));
        assertEquals(RecordStatus.HIDDEN, created.status());
        assertEquals(0L, created.version());
        assertEquals(2L, linkRepository.countByCollectionId(created.id()));
        assertEquals(0L, collectionService.getAllCollections(PageRequest.of(0, 20)).content().stream()
                .filter(row -> row.id().equals(created.id())).findFirst().orElseThrow().version());

        var mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        String detailPath = "/api/v1/collections/admin/" + created.id();
        // Web khác origin dùng Authorization + Content-Type; không còn cần version header riêng.
        mvc.perform(options(detailPath + "/products").contextPath("/api/v1")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(get(detailPath).contextPath("/api/v1").with(user("viewer").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(patch(detailPath).contextPath("/api/v1")
                        .with(user("viewer").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(detailPath).contextPath("/api/v1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("HIDDEN"))
                .andExpect(jsonPath("$.result.version").value(0));
        mvc.perform(put(detailPath + "/products").contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[\"" + removedLater.getId() + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.expectedVersion").exists());
        mvc.perform(patch(detailPath + "/publish").contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.expectedVersion").exists());
        mvc.perform(delete(detailPath).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());

        inactiveLater.setStatus(RecordStatus.HIDDEN);
        productRepository.saveAndFlush(inactiveLater);
        var adminView = collectionService.getCollectionById(created.id());
        assertEquals(1L, adminView.visibleProductCount());
        assertEquals(RecordStatus.HIDDEN, adminView.products().get(0).status());

        // Web/mobile gửi số trong JSON, không cần If-Match; chỉ đổi thứ tự vẫn tăng version.
        mvc.perform(put(detailPath + "/products").contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"productIds\":[\"" + removedLater.getId()
                                + "\",\"" + inactiveLater.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.version").value(1));
        var reordered = collectionService.getCollectionById(created.id());
        assertEquals(1L, reordered.version());
        assertEquals(0, reordered.products().get(0).sortOrder());
        assertEquals(1, reordered.products().get(1).sortOrder());
        assertEquals(1L, collectionService.getCollectionById(created.id()).version());
        mvc.perform(put(detailPath + "/products").contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"productIds\":[\"" + newlyAdded.getId() + "\"]}"))
                .andExpect(status().isConflict());

        // PUT đầy đủ: bỏ một link, thêm link mới; Product bị bỏ không bị xoá.
        var replaced = collectionService.replaceProducts(created.id(),
                new CollectionProductsRequest(1L, List.of(inactiveLater.getId(), newlyAdded.getId())));
        assertEquals(2L, replaced.version());
        assertEquals(1L, replaced.visibleProductCount());
        assertEquals(2L, linkRepository.countByCollectionId(created.id()));
        assertTrue(productRepository.findById(removedLater.getId()).isPresent());

        ApiException cannotLinkHidden = assertThrows(ApiException.class, () -> collectionService.replaceProducts(
                created.id(), new CollectionProductsRequest(2L, List.of(inactiveLater.getId(), cannotAdd.getId()))));
        assertEquals(ErrorCode.CONFLICT, cannotLinkHidden.getErrorCode());
        assertEquals(2L, collectionService.getCollectionById(created.id()).version());

        mvc.perform(patch(detailPath + "/publish").contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.version").value(3))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
        mvc.perform(patch(detailPath + "/hide").contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.version").value(4))
                .andExpect(jsonPath("$.result.status").value("HIDDEN"));
        mvc.perform(patch(detailPath).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":4,\"collectionName\":\"Updated collection\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.version").value(5))
                .andExpect(jsonPath("$.result.collectionName").value("Updated collection"));
        mvc.perform(delete(detailPath).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .queryParam("expectedVersion", "4"))
                .andExpect(status().isConflict());
        mvc.perform(delete(detailPath).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .queryParam("expectedVersion", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(detailPath).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .queryParam("expectedVersion", "5"))
                .andExpect(status().isOk());
        assertEquals(RecordStatus.DELETED, collectionRepository.findById(created.id()).orElseThrow().getStatus());
        assertEquals(0L, linkRepository.countByCollectionId(created.id()));
        assertTrue(productRepository.findById(newlyAdded.getId()).isPresent());
        assertTrue(collectionService.getAllCollections(PageRequest.of(0, 20)).content().stream()
                .noneMatch(row -> row.id().equals(created.id())));
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(ApiException.class,
                () -> collectionService.getCollectionById(created.id())).getErrorCode());
    }

    @Test
    void publicListIsPagedAndNeverLeaksDraftsOrHiddenProducts() throws Exception {
        long baseline = collectionService.getPublicCollections(PageRequest.of(0, 1)).totalElements();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        // Ẩn Category chỉ ẩn mục duyệt theo Category, không ẩn Product khỏi BST.
        CategoryEntity category = categoryRepository.save(CategoryEntity.builder()
                .categoryName("Public list " + suffix)
                .slug("public-list-" + suffix)
                .status(RecordStatus.HIDDEN)
                .build());
        ProductEntity product = createProduct(category, suffix, "public", RecordStatus.ACTIVE);
        var published = collectionService.createCollection(new CollectionCreateRequest(
                "Public " + suffix, "public-" + suffix, null, List.of(product.getId())));
        var draft = collectionService.createCollection(new CollectionCreateRequest(
                "Draft " + suffix, "draft-" + suffix, null, List.of(product.getId())));
        assertEquals(baseline, collectionService.getPublicCollections(PageRequest.of(0, 1)).totalElements());

        collectionService.publishCollection(published.id(), new CollectionVersionRequest(0L));
        var visiblePage = collectionService.getPublicCollections(PageRequest.of(0, 1));
        assertEquals(baseline + 1, visiblePage.totalElements());
        assertEquals(1, visiblePage.size());

        var mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        String publicPath = "/api/v1/collections";
        String adminPath = "/api/v1/collections/admin";
        mvc.perform(get(publicPath).contextPath("/api/v1").queryParam("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value((int) (baseline + 1)))
                .andExpect(jsonPath("$.result.content[0].status").doesNotExist())
                .andExpect(jsonPath("$.result.content[0].version").doesNotExist());
        mvc.perform(get(publicPath).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value((int) (baseline + 1)))
                .andExpect(jsonPath("$.result.content[0].status").doesNotExist());
        mvc.perform(get(adminPath).contextPath("/api/v1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(adminPath).contextPath("/api/v1").with(user("viewer").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get(adminPath).contextPath("/api/v1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get(publicPath).contextPath("/api/v1").queryParam("sort", "products.size,asc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(adminPath).contextPath("/api/v1").queryParam("sort", "products.size,asc")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        assertTrue(collectionService.getAllCollections(PageRequest.of(0, 200)).content().stream()
                .anyMatch(row -> row.id().equals(draft.id())));

        product.setStatus(RecordStatus.HIDDEN);
        productRepository.saveAndFlush(product);
        assertEquals(baseline, collectionService.getPublicCollections(PageRequest.of(0, 1)).totalElements());
        assertEquals(RecordStatus.ACTIVE,
                collectionRepository.findById(published.id()).orElseThrow().getStatus());

        // Chỉ Product.status null được coi là hiển thị; BST chưa publish (status null) thì không.
        product.setStatus(null);
        productRepository.saveAndFlush(product);
        assertEquals(baseline + 1, collectionService.getPublicCollections(PageRequest.of(0, 1)).totalElements());
        var collection = collectionRepository.findById(published.id()).orElseThrow();
        collection.setStatus(null);
        collectionRepository.saveAndFlush(collection);
        assertEquals(baseline, collectionService.getPublicCollections(PageRequest.of(0, 1)).totalElements());
    }

    @Test
    void publicDetailFiltersProductsBeforePagingAndKeepsAdminLinksPrivate() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CategoryEntity hiddenCategory = categoryRepository.save(CategoryEntity.builder()
                .categoryName("Detail test " + suffix)
                .slug("detail-cat-" + suffix)
                .status(RecordStatus.HIDDEN)
                .build());
        ProductEntity first = createProduct(hiddenCategory, suffix, "first", RecordStatus.ACTIVE);
        ProductEntity hidden = createProduct(hiddenCategory, suffix, "hidden", RecordStatus.ACTIVE);
        ProductEntity legacy = createProduct(hiddenCategory, suffix, "legacy", RecordStatus.ACTIVE);
        ProductEntity deleted = createProduct(hiddenCategory, suffix, "deleted", RecordStatus.ACTIVE);
        String slug = "detail-" + suffix;
        var created = collectionService.createCollection(new CollectionCreateRequest(
                "Detail " + suffix, slug, "https://example.com/cover.jpg",
                List.of(first.getId(), hidden.getId(), legacy.getId(), deleted.getId())));
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(ApiException.class,
                () -> collectionService.getPublicCollectionBySlug(slug, PageRequest.of(0, 1))).getErrorCode());
        collectionService.publishCollection(created.id(), new CollectionVersionRequest(0L));
        hidden.setStatus(RecordStatus.HIDDEN);
        productRepository.saveAndFlush(hidden);
        legacy.setStatus(null); // Product cũ vẫn hiển thị.
        productRepository.saveAndFlush(legacy);
        deleted.setStatus(RecordStatus.DELETED);
        productRepository.saveAndFlush(deleted);

        var firstPage = collectionService.getPublicCollectionBySlug(slug, PageRequest.of(0, 1));
        assertEquals(created.id(), firstPage.id());
        assertEquals("https://example.com/cover.jpg", firstPage.coverImageUrl());
        assertEquals(2L, firstPage.products().totalElements());
        assertEquals(first.getId(), firstPage.products().content().get(0).productId());
        assertEquals(new BigDecimal("100000.00"), firstPage.products().content().get(0).price());
        var secondPage = collectionService.getPublicCollectionBySlug(slug, PageRequest.of(1, 1));
        assertEquals(legacy.getId(), secondPage.products().content().get(0).productId());
        var beyondLastPage = collectionService.getPublicCollectionBySlug(slug, PageRequest.of(2, 1));
        assertEquals(2L, beyondLastPage.products().totalElements());
        assertTrue(beyondLastPage.products().content().isEmpty());
        assertEquals(4, collectionService.getCollectionById(created.id()).products().size());

        var mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        String publicPath = "/api/v1/collections/slug/" + slug;
        String adminPath = "/api/v1/collections/admin/" + created.id();
        mvc.perform(get(publicPath).contextPath("/api/v1").queryParam("page", "0").queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.slug").value(slug))
                .andExpect(jsonPath("$.result.status").doesNotExist())
                .andExpect(jsonPath("$.result.version").doesNotExist())
                .andExpect(jsonPath("$.result.products.totalElements").value(2))
                .andExpect(jsonPath("$.result.products.content[0].productId").value(first.getId().toString()))
                .andExpect(jsonPath("$.result.products.content[0].status").doesNotExist());
        mvc.perform(get(publicPath).contextPath("/api/v1").queryParam("page", "1")
                        .queryParam("size", "1").with(user("viewer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.products.content[0].productId").value(legacy.getId().toString()));
        mvc.perform(get(publicPath).contextPath("/api/v1").queryParam("page", "2")
                        .queryParam("size", "1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.products.totalElements").value(2))
                .andExpect(jsonPath("$.result.products.content").isEmpty());
        mvc.perform(get(publicPath).contextPath("/api/v1").queryParam("sort", "sortOrder,desc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(adminPath).contextPath("/api/v1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.products.length()").value(4))
                .andExpect(jsonPath("$.result.version").exists());
        mvc.perform(get("/api/v1/collections/slug/missing-" + suffix).contextPath("/api/v1"))
                .andExpect(status().isNotFound());

        // Khi mọi Product ngừng hiển thị, BST vẫn ACTIVE cho admin nhưng ẩn hoàn toàn khỏi khách.
        first.setStatus(RecordStatus.HIDDEN);
        productRepository.saveAndFlush(first);
        legacy.setStatus(RecordStatus.DELETED);
        productRepository.saveAndFlush(legacy);
        mvc.perform(get(publicPath).contextPath("/api/v1"))
                .andExpect(status().isNotFound());
        assertEquals(RecordStatus.ACTIVE,
                collectionRepository.findById(created.id()).orElseThrow().getStatus());
        first.setStatus(RecordStatus.ACTIVE);
        productRepository.saveAndFlush(first);
        mvc.perform(get(publicPath).contextPath("/api/v1"))
                .andExpect(status().isOk());
        collectionService.hideCollection(created.id(), new CollectionVersionRequest(1L));
        mvc.perform(get(publicPath).contextPath("/api/v1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void simultaneousReplacementsRejectTheStaleAdminInsteadOfLosingAnUpdate() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CategoryEntity category = categoryRepository.save(CategoryEntity.builder()
                .categoryName("Concurrent test " + suffix)
                .slug("concurrent-test-" + suffix)
                .status(RecordStatus.ACTIVE)
                .build());
        ProductEntity original = createProduct(category, suffix, "base", RecordStatus.ACTIVE);
        ProductEntity adminAAdds = createProduct(category, suffix, "admin-a", RecordStatus.ACTIVE);
        ProductEntity adminBAdds = createProduct(category, suffix, "admin-b", RecordStatus.ACTIVE);
        var created = collectionService.createCollection(new CollectionCreateRequest(
                "Concurrent " + suffix, "concurrent-" + suffix, null, List.of(original.getId())));
        assertEquals(0L, created.version());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ErrorCode> adminA = () -> replaceAfterSignal(
                    created.id(), original.getId(), adminAAdds.getId(), ready, start);
            Callable<ErrorCode> adminB = () -> replaceAfterSignal(
                    created.id(), original.getId(), adminBAdds.getId(), ready, start);
            var first = pool.submit(adminA);
            var second = pool.submit(adminB);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<ErrorCode> outcomes = java.util.Arrays.asList(
                    first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));

            assertEquals(1L, outcomes.stream().filter(Objects::isNull).count());
            assertEquals(1L, outcomes.stream().filter(ErrorCode.CONFLICT::equals).count());
            var detail = collectionService.getCollectionById(created.id());
            assertEquals(1L, detail.version());
            assertEquals(2, detail.products().size());
            UUID savedAddition = detail.products().get(1).productId();
            assertTrue(savedAddition.equals(adminAAdds.getId()) || savedAddition.equals(adminBAdds.getId()));
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private ErrorCode replaceAfterSignal(UUID collectionId, UUID originalId, UUID addedId,
                                         CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent writers did not start together");
        }
        try {
            collectionService.replaceProducts(collectionId,
                    new CollectionProductsRequest(0L, List.of(originalId, addedId)));
            return null; // success
        } catch (ApiException ex) {
            return ex.getErrorCode();
        }
    }

    private ProductEntity createProduct(CategoryEntity category, String suffix, String key, RecordStatus status) {
        return productRepository.save(ProductEntity.builder()
                .name("Shirt test " + key)
                .slug("shirt-test-" + suffix + "-" + key)
                .sku("COL-" + suffix + "-" + key)
                .price(new BigDecimal("100000.00"))
                .category(category)
                .status(status)
                .build());
    }
}
