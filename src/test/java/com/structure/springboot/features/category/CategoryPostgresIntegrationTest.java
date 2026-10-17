package com.structure.springboot.features.category;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.category.dto.request.CategoryRequest;
import com.structure.springboot.features.category.dto.request.CategoryUpdateRequest;
import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.category.service.CategoryService;
import com.structure.springboot.features.product.dto.request.ProductFilter;
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
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
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

/** Opt-in PostgreSQL TEST database only; never point DB_* to a production database. */
@EnabledIfEnvironmentVariable(named = "RUN_CATEGORY_DB_TESTS", matches = "true")
@SpringBootTest
@TestPropertySource(properties = {
        "app.data-init.enabled=false",
        "spring.docker.compose.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration"
})
class CategoryPostgresIntegrationTest {

    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private CategoryService categoryService;
    @Autowired private ProductService productService;
    @Autowired private WebApplicationContext webContext;
    @Autowired private JsonMapper jsonMapper;

    @Test
    void publicOnlyShowsActiveEvenEmptyAndAdminIsProtectedFromWildcardWhitelist() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long before = categoryService.getPublicCategories(PageRequest.of(0, 1)).totalElements();
        CategoryEntity active = category(suffix, "active", RecordStatus.ACTIVE);
        CategoryEntity hidden = category(suffix, "hidden", RecordStatus.HIDDEN);
        CategoryEntity legacy = category(suffix, "legacy", null);
        CategoryEntity deleted = category(suffix, "deleted", RecordStatus.DELETED);
        var page = PageRequest.of(0, 1000, Sort.by("categoryName"));

        // COUNT/phân trang tại DB: ba hàng HIDDEN/null/DELETED không được tính vào public.
        assertEquals(before + 1, categoryService.getPublicCategories(PageRequest.of(0, 1)).totalElements());
        var publicRows = categoryService.getPublicCategories(page).content();
        assertTrue(publicRows.stream().anyMatch(row -> row.id().equals(active.getId()))); // ACTIVE rỗng vẫn public
        assertTrue(publicRows.stream().noneMatch(row -> row.id().equals(hidden.getId())
                || row.id().equals(legacy.getId()) || row.id().equals(deleted.getId())));
        var adminRows = categoryService.getAdminCategories(page).content();
        assertTrue(adminRows.stream().anyMatch(row -> row.id().equals(hidden.getId())));
        assertTrue(adminRows.stream().anyMatch(row -> row.id().equals(legacy.getId())));
        assertTrue(adminRows.stream().noneMatch(row -> row.id().equals(deleted.getId())));

        var mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        String base = "/api/v1/categories";
        var guest = jsonMapper.readTree(mvc.perform(get(base).contextPath("/api/v1")
                        .queryParam("sort", "categoryName,asc"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("result");
        var viewer = jsonMapper.readTree(mvc.perform(get(base).contextPath("/api/v1")
                        .queryParam("sort", "categoryName,asc").with(user("viewer").roles("USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("result");
        var admin = jsonMapper.readTree(mvc.perform(get(base).contextPath("/api/v1")
                        .queryParam("sort", "categoryName,asc").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("result");
        assertEquals(guest, viewer);
        assertEquals(guest, admin);

        mvc.perform(get(base + "/id/" + active.getId()).contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.slug").value(active.getSlug()))
                .andExpect(jsonPath("$.result.status").doesNotExist())
                .andExpect(jsonPath("$.result.createdAt").doesNotExist());
        mvc.perform(get(base + "/slug/" + active.getSlug()).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.updatedAt").doesNotExist());
        for (CategoryEntity notPublic : new CategoryEntity[]{hidden, legacy, deleted}) {
            mvc.perform(get(base + "/id/" + notPublic.getId()).contextPath("/api/v1"))
                    .andExpect(status().isNotFound());
            mvc.perform(get(base + "/slug/" + notPublic.getSlug()).contextPath("/api/v1")
                            .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isNotFound());
        }
        // Cũ /categories/{id} không nằm trong whitelist, /categories/admin cũng không.
        mvc.perform(get(base + "/" + active.getId()).contextPath("/api/v1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(base + "/admin").contextPath("/api/v1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(base + "/admin").contextPath("/api/v1").with(user("viewer").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get(base + "/admin").contextPath("/api/v1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get(base + "/admin/" + hidden.getId()).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("HIDDEN"));
        mvc.perform(get(base + "/admin/" + legacy.getId()).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get(base + "/admin/" + deleted.getId()).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(post(base + "/admin").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(base + "/admin/" + active.getId()).contextPath("/api/v1")
                        .with(user("viewer").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(base + "/admin/" + active.getId()).contextPath("/api/v1")
                        .with(user("viewer").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get(base).contextPath("/api/v1").queryParam("sort", "name,asc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(base + "/admin").contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN")).queryParam("sort", "status,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categoryRenameAutogeneratesSlugUnlessExplicitAndKeepsWritesAtomic() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        String base = "/api/v1/categories";
        String originalName = "2C4 Original " + suffix;
        mvc.perform(post(base + "/admin").contextPath("/api/v1").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"" + originalName + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.slug").value("2c4-original-" + suffix));
        CategoryEntity saved = categories.findBySlug("2c4-original-" + suffix).orElseThrow();
        UUID id = saved.getId();
        String oldSlug = saved.getSlug();

        // Đổi tên + thiếu slug => tự sinh lại; URL slug cũ không có alias.
        mvc.perform(patch(base + "/admin/" + id).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"2C4 Renamed " + suffix + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.slug").value("2c4-renamed-" + suffix));
        mvc.perform(get(base + "/slug/" + oldSlug).contextPath("/api/v1"))
                .andExpect(status().isNotFound());
        mvc.perform(get(base + "/slug/2c4-renamed-" + suffix).contextPath("/api/v1"))
                .andExpect(status().isOk());

        // Đổi tên + slug="" cũng tự sinh, slug tường minh sẽ ghi đè tên sinh tự động.
        mvc.perform(patch(base + "/admin/" + id).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"2C4 Empty " + suffix + "\",\"slug\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.slug").value("2c4-empty-" + suffix));
        mvc.perform(patch(base + "/admin/" + id).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"2C4 Null " + suffix + "\",\"slug\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.slug").value("2c4-null-" + suffix));
        mvc.perform(patch(base + "/admin/" + id).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"2C4 Manual " + suffix
                                + "\",\"slug\":\"my-custom-" + suffix + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.slug").value("my-custom-" + suffix));
        // Gửi lại tên y hệt không tính là đổi tên thật sự: giữ slug tùy chọn.
        mvc.perform(patch(base + "/admin/" + id).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"2C4 Manual " + suffix + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.slug").value("my-custom-" + suffix));
        mvc.perform(patch(base + "/admin/" + id).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slug\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(base + "/admin/" + id).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slug\":\"BAD slug\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(base + "/admin/" + id).contextPath("/api/v1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELETED\"}"))
                .andExpect(status().isBadRequest());

        // Kể cả slug do Category DELETED giữ cũng không được dùng lại; lỗi không đổi tên/status.
        String collideName = "2C4 Collide " + suffix;
        CategoryEntity reservedBySlug = categories.saveAndFlush(CategoryEntity.builder()
                .categoryName("2C4 Reserved " + suffix).slug("2c4-collide-" + suffix)
                .status(RecordStatus.DELETED).build());
        assertEquals("2c4-collide-" + suffix, reservedBySlug.getSlug());
        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class,
                () -> categoryService.updateCategory(id,
                        new CategoryUpdateRequest(collideName, null, RecordStatus.HIDDEN))).getErrorCode());
        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class,
                () -> categoryService.updateCategory(id,
                        new CategoryUpdateRequest("2C4 Reserved " + suffix, "another-" + suffix,
                                RecordStatus.HIDDEN))).getErrorCode());
        assertEquals("my-custom-" + suffix, categories.findById(id).orElseThrow().getSlug());
        assertEquals("2C4 Manual " + suffix, categories.findById(id).orElseThrow().getCategoryName());
        assertEquals(RecordStatus.ACTIVE, categories.findById(id).orElseThrow().getStatus());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(ApiException.class,
                () -> categoryService.updateCategory(id,
                        new CategoryUpdateRequest("    ", null, null))).getErrorCode());
    }

    @Test
    void hidingCategoryDoesNotHideItsProductAndDeleteOnlyChecksNonDeletedProducts() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CategoryEntity category = category(suffix, "with-product", RecordStatus.ACTIVE);
        ProductEntity product = product(category, suffix, "one", RecordStatus.ACTIVE);
        CategoryUpdateRequest hide = new CategoryUpdateRequest(null, null, RecordStatus.HIDDEN);
        categoryService.updateCategory(category.getId(), hide);

        assertEquals(category.getId(), products.findById(product.getId()).orElseThrow().getCategory().getId());
        assertEquals(product.getId(), productService.getProductById(product.getId()).id());
        assertTrue(productService.getAllProducts(new ProductFilter(null, null, product.getName(),
                null, null, null), PageRequest.of(0, 100)).content().stream()
                .anyMatch(row -> row.id().equals(product.getId())));
        assertEquals(0, productService.getAllProducts(new ProductFilter(category.getSlug(), null,
                null, null, null, null), PageRequest.of(0, 10)).totalElements());
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(ApiException.class,
                () -> categoryService.getPublicCategoryBySlug(category.getSlug())).getErrorCode());

        var mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        String adminPath = "/api/v1/categories/admin/" + category.getId();
        // PATCH không được lách điều kiện xóa bằng status=DELETED.
        mvc.perform(patch(adminPath).contextPath("/api/v1").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELETED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(adminPath).contextPath("/api/v1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict());

        // HIDDEN/legacy null Product vẫn chặn xóa; DELETED Product thì không.
        for (RecordStatus status : new RecordStatus[]{RecordStatus.ACTIVE, RecordStatus.HIDDEN, null}) {
            product.setStatus(status);
            products.saveAndFlush(product);
            assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class,
                    () -> categoryService.deleteCategory(category.getId())).getErrorCode());
        }
        product.setStatus(RecordStatus.DELETED);
        products.saveAndFlush(product);
        mvc.perform(delete(adminPath).contextPath("/api/v1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        assertEquals(RecordStatus.DELETED, categories.findById(category.getId()).orElseThrow().getStatus());
        assertEquals(category.getId(), products.findById(product.getId()).orElseThrow().getCategory().getId());
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(ApiException.class,
                () -> categoryService.getAdminCategoryById(category.getId())).getErrorCode());
        assertEquals(ErrorCode.CONFLICT, assertThrows(ApiException.class,
                () -> categoryService.createCategory(new CategoryRequest("Another " + suffix,
                        category.getSlug()))).getErrorCode());
    }

    private CategoryEntity category(String suffix, String label, RecordStatus status) {
        return categories.saveAndFlush(CategoryEntity.builder()
                .categoryName("2C4 " + label + " " + suffix)
                .slug("2c4-" + label + "-" + suffix).status(status).build());
    }

    private ProductEntity product(CategoryEntity category, String suffix, String label, RecordStatus status) {
        return products.saveAndFlush(ProductEntity.builder().name("2C4 Product " + label)
                .slug("2c4-product-" + suffix + "-" + label).sku("CAT-2C4-" + suffix + "-" + label)
                .price(new BigDecimal("100.00")).category(category).status(status).build());
    }
}
