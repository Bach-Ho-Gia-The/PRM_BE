package com.structure.springboot.features.product;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.collection.dto.request.CollectionCreateRequest;
import com.structure.springboot.features.collection.dto.request.CollectionVersionRequest;
import com.structure.springboot.features.collection.repository.CollectionRepository;
import com.structure.springboot.features.collection.service.CollectionService;
import com.structure.springboot.features.product.dto.request.ProductFilter;
import com.structure.springboot.features.product.dto.request.ProductRequest;
import com.structure.springboot.features.product.dto.request.ProductUpdateRequest;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.service.ProductService;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Opt-in trên PostgreSQL test riêng, tuyệt đối không dùng DB thật. */
@EnabledIfEnvironmentVariable(named = "RUN_PRODUCT_DB_TESTS", matches = "true")
@SpringBootTest
@TestPropertySource(properties = {
        "app.data-init.enabled=false",
        "spring.docker.compose.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration"
})
class ProductPostgresIntegrationTest {

    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private CollectionRepository collections;
    @Autowired private ProductService productService;
    @Autowired private CollectionService collectionService;
    @Autowired private WebApplicationContext webContext;

    @Test
    void optionalFiltersUseRealPublishedMembershipBeforePagingAndProtectAdminRoutes() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CategoryEntity hiddenCategory = category(suffix, "hidden", RecordStatus.HIDDEN);
        CategoryEntity activeCategory = category(suffix, "active", RecordStatus.ACTIVE);
        ProductEntity hiddenCatActive = product(hiddenCategory, suffix, "a", RecordStatus.ACTIVE, "100.00");
        ProductEntity laterHidden = product(activeCategory, suffix, "b", RecordStatus.ACTIVE, "150.00");
        ProductEntity legacyVisible = product(activeCategory, suffix, "c", RecordStatus.ACTIVE, "250.00");
        ProductEntity laterDeleted = product(activeCategory, suffix, "d", RecordStatus.ACTIVE, "300.00");
        String slug = "product-filter-" + suffix;
        var created = collectionService.createCollection(new CollectionCreateRequest(
                "Product filter " + suffix, slug, null,
                List.of(hiddenCatActive.getId(), laterHidden.getId(), legacyVisible.getId(), laterDeleted.getId())));
        collectionService.publishCollection(created.id(), new CollectionVersionRequest(0L));
        laterHidden.setStatus(RecordStatus.HIDDEN);
        products.saveAndFlush(laterHidden);
        legacyVisible.setStatus(null);
        products.saveAndFlush(legacyVisible);
        laterDeleted.setStatus(RecordStatus.DELETED);
        products.saveAndFlush(laterDeleted);

        ProductFilter filter = new ProductFilter(null, slug, null, null, null, null);
        var page0 = productService.getAllProducts(filter, PageRequest.of(0, 1, Sort.by("price")));
        assertEquals(2, page0.totalElements());
        assertEquals(hiddenCatActive.getId(), page0.content().get(0).id());
        var page1 = productService.getAllProducts(filter, PageRequest.of(1, 1, Sort.by("price")));
        assertEquals(legacyVisible.getId(), page1.content().get(0).id());
        assertEquals(2, page1.totalElements());
        assertEquals(1, productService.getAllProducts(new ProductFilter(null, slug, null,
                new BigDecimal("200"), null, null), PageRequest.of(0, 1)).totalElements());
        // Category HIDDEN chỉ làm trống trang khi client CHỌN filter category.
        assertEquals(0, productService.getAllProducts(new ProductFilter(hiddenCategory.getSlug(), slug,
                null, null, null, null), PageRequest.of(0, 10)).totalElements());
        assertEquals(1, productService.getAllProducts(new ProductFilter(activeCategory.getSlug(), slug,
                null, null, null, null), PageRequest.of(0, 10)).totalElements());
        assertEquals(4, collectionService.getCollectionById(created.id()).products().size());
        var adminRows = productService.getAdminProducts(PageRequest.of(0, 1000)).content();
        assertTrue(adminRows.stream().anyMatch(row -> row.id().equals(laterHidden.getId())));
        assertTrue(adminRows.stream().noneMatch(row -> row.id().equals(laterDeleted.getId())));

        var mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        String publicPath = "/api/v1/products";
        String adminPath = "/api/v1/products/admin";
        mvc.perform(get(publicPath).contextPath("/api/v1").queryParam("collection", slug)
                        .queryParam("size", "1").queryParam("sort", "price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.content[0].id").value(hiddenCatActive.getId().toString()))
                .andExpect(jsonPath("$.result.content[0].collection").doesNotExist());
        mvc.perform(get(publicPath).contextPath("/api/v1").queryParam("collection", slug)
                        .queryParam("size", "1").queryParam("sort", "price,asc")
                        .with(user("viewer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(2));
        mvc.perform(get(publicPath).contextPath("/api/v1").queryParam("collection", slug)
                        .queryParam("size", "1").queryParam("sort", "price,asc")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(2));
        mvc.perform(get(publicPath + "/id/" + hiddenCatActive.getId()).contextPath("/api/v1"))
                .andExpect(status().isOk());
        mvc.perform(get(publicPath + "/slug/" + hiddenCatActive.getSlug()).contextPath("/api/v1"))
                .andExpect(status().isOk());
        mvc.perform(get(publicPath + "/id/" + laterHidden.getId()).contextPath("/api/v1"))
                .andExpect(status().isNotFound());
        // Đường /products/{id} cũ không còn là API công khai; chỉ /products/id/{id} được whitelist.
        mvc.perform(get(publicPath + "/" + hiddenCatActive.getId()).contextPath("/api/v1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(adminPath + "/" + laterHidden.getId()).contextPath("/api/v1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(adminPath + "/" + laterHidden.getId()).contextPath("/api/v1")
                        .with(user("viewer").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get(adminPath + "/" + laterHidden.getId()).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("HIDDEN"));
        mvc.perform(get(adminPath).contextPath("/api/v1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(adminPath).contextPath("/api/v1").with(user("viewer").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get(adminPath).contextPath("/api/v1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(post(adminPath).contextPath("/api/v1").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(adminPath + "/" + hiddenCatActive.getId()).contextPath("/api/v1")
                        .with(user("viewer").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(publicPath).contextPath("/api/v1").queryParam("collection", "SUNSET"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(publicPath).contextPath("/api/v1").queryParam("sort", "status,asc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(publicPath).contextPath("/api/v1").queryParam("collection", hiddenCollection(suffix, hiddenCatActive)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(0));

        collectionService.hideCollection(created.id(), new CollectionVersionRequest(1L));
        assertEquals(0, productService.getAllProducts(filter, PageRequest.of(0, 1)).totalElements());
        assertTrue(products.findById(hiddenCatActive.getId()).isPresent());
        assertEquals(RecordStatus.HIDDEN, collections.findById(created.id()).orElseThrow().getStatus());
    }

    @Test
    void productCanOnlyChangeToActiveCategoryButMayKeepExistingHiddenCategory() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CategoryEntity original = category(suffix, "original", RecordStatus.ACTIVE);
        CategoryEntity hidden = category(suffix, "hidden", RecordStatus.HIDDEN);
        CategoryEntity active = category(suffix, "new", RecordStatus.ACTIVE);
        ProductEntity product = product(original, suffix, "entry", RecordStatus.ACTIVE, "130.00");
        ProductRequest createInHidden = new ProductRequest("Test", null, "QA-" + suffix, new BigDecimal("100"),
                null, null, null, null, null, null, false, false, hidden.getId());
        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class,
                () -> productService.createProduct(createInHidden)).getErrorCode());
        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class,
                () -> productService.updateProduct(product.getId(), update(null, hidden.getId()))).getErrorCode());

        // Category đổi HIDDEN sau khi Product đã liên kết: vẫn được sửa tên Product.
        original.setStatus(RecordStatus.HIDDEN);
        categories.saveAndFlush(original);
        productService.updateProduct(product.getId(), update("Still editable", original.getId()));
        assertEquals(RecordStatus.HIDDEN,
                products.findById(product.getId()).orElseThrow().getCategory().getStatus());
        productService.updateProduct(product.getId(), update(null, active.getId()));
        assertEquals(active.getId(), products.findById(product.getId()).orElseThrow().getCategory().getId());

        // Tên không tự đổi slug; gửi tên+slug mới trong một PATCH thì URL cũ ngừng hoạt động.
        String oldSlug = product.getSlug();
        productService.updateProduct(product.getId(), new ProductUpdateRequest("New name",
                "new-product-" + suffix, null, null, null, null, null, null,
                null, null, null, null, null, null));
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(ApiException.class,
                () -> productService.getProductBySlug(oldSlug)).getErrorCode());
        assertEquals(product.getId(), productService.getProductBySlug("new-product-" + suffix).id());

        var mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        String path = "/api/v1/products/admin";
        String sku = "HTTP-" + suffix;
        mvc.perform(post(path).contextPath("/api/v1").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"HTTP test " + suffix + "\",\"sku\":\"" + sku
                                + "\",\"price\":100,\"categoryId\":\"" + active.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.collection").doesNotExist());
        UUID id = products.findBySku(sku).orElseThrow().getId();
        mvc.perform(patch(path + "/" + id).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("HIDDEN"));
        mvc.perform(get("/api/v1/products/id/" + id).contextPath("/api/v1"))
                .andExpect(status().isNotFound());
        mvc.perform(get(path + "/" + id).contextPath("/api/v1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(delete(path + "/" + id).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get(path + "/" + id).contextPath("/api/v1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    private String hiddenCollection(String suffix, ProductEntity linked) {
        String slug = "hidden-product-filter-" + suffix;
        collectionService.createCollection(new CollectionCreateRequest(
                "Hidden filter " + suffix, slug, null, List.of(linked.getId())));
        return slug;
    }

    private CategoryEntity category(String suffix, String name, RecordStatus status) {
        return categories.save(CategoryEntity.builder().categoryName("Product test " + name + " " + suffix)
                .slug("product-test-" + name + "-" + suffix).status(status).build());
    }

    private ProductEntity product(CategoryEntity category, String suffix, String name,
                                  RecordStatus status, String price) {
        return products.saveAndFlush(ProductEntity.builder().name("Product test " + name)
                .slug("product-test-" + suffix + "-" + name).sku("P2C3-" + suffix + "-" + name)
                .price(new BigDecimal(price)).category(category).status(status).build());
    }

    private ProductUpdateRequest update(String name, UUID categoryId) {
        return new ProductUpdateRequest(name, null, null, null, null, null, null, null,
                null, null, null, null, null, categoryId);
    }
}
