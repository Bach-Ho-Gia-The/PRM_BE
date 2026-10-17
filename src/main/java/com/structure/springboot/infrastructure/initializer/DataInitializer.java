package com.structure.springboot.infrastructure.initializer;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.store.entity.StoreEntity;
import com.structure.springboot.features.store.repository.StoreRepository;
import com.structure.springboot.features.collection.dto.request.CollectionCreateRequest;
import com.structure.springboot.features.collection.dto.request.CollectionVersionRequest;
import com.structure.springboot.features.collection.repository.CollectionRepository;
import com.structure.springboot.features.collection.service.CollectionService;
import com.structure.springboot.features.product.entity.ProductEntity;
import com.structure.springboot.features.product.entity.ProductImageEntity;
import com.structure.springboot.features.product.entity.ProductVariantEntity;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.role.entity.RoleEntity;
import com.structure.springboot.features.role.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tạo dữ liệu demo: role, tài khoản, Category, Product và BST Sunset thật. Với DB dev/test cũ
 * còn products.collection, chạy script backfill dev_only trước khi bật initializer mới.
 *
 * <p><b>Idempotent</b> — mỗi lần khởi động kiểm tra tồn tại trước khi ghi; không ghi đè
 * mật khẩu hoặc thứ tự Product trong BST mà admin đã sửa.</p>
 *
 * <p>Tắt bằng {@code app.data-init.enabled=false} khi deploy thật.</p>
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final CollectionRepository collectionRepository;
    private final CollectionService collectionService;
    private final PasswordEncoder passwordEncoder;
    private final JsonMapper jsonMapper;

    @Value("${app.data-init.enabled:true}")
    private boolean enabled;

    @Value("${app.data-init.admin-password:Admin@12345}")
    private String adminPassword;

    @Value("${app.data-init.user-password:User@12345}")
    private String userPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }

        RoleEntity adminRole = ensureRole("ADMIN", "Full access to every resource");
        RoleEntity userRole = ensureRole("USER", "Default role for end users");

        ensureAccount("admin@example.com", adminPassword, adminRole);
        ensureAccount("user@example.com", userPassword, userRole);

        ensureCategory("Áo & Đầm", "ao");
        ensureCategory("Quần & Chân Váy", "quan");
        ensureCatalog();
        ensureSeedCollections();
        ensureStores();
    }

    /**
     * Bốn chi nhánh mẫu cho màn bản đồ.
     *
     * <p>Toạ độ là số thật ở TP.HCM để ghim hiện đúng vị trí khi demo. Tra theo tên nên chạy lại
     * nhiều lần không sinh bản ghi trùng.</p>
     */
    private void ensureStores() {
        ensureStore("Chi nhánh Quận 1", "72 Lê Thánh Tôn, Bến Nghé, Quận 1",
                "0901234501", "08:00 - 22:00", 10.77689, 106.70098);
        ensureStore("Chi nhánh Quận 3", "189 Võ Văn Tần, Phường 5, Quận 3",
                "0901234502", "08:30 - 21:30", 10.77459, 106.68741);
        ensureStore("Chi nhánh Thủ Đức", "216 Võ Văn Ngân, Bình Thọ, Thủ Đức",
                "0901234503", "09:00 - 22:00", 10.85072, 106.75700);
        ensureStore("Chi nhánh Tân Bình", "202 Hoàng Văn Thụ, Phường 9, Tân Bình",
                "0901234504", "08:00 - 21:00", 10.79838, 106.66305);
    }

    private void ensureStore(String storeName, String address, String phone,
                             String openingHours, double latitude, double longitude) {
        if (storeRepository.existsByStoreNameIgnoreCase(storeName)) {
            return;
        }
        storeRepository.save(StoreEntity.builder()
                .storeName(storeName)
                .address(address)
                .phone(phone)
                .openingHours(openingHours)
                .latitude(latitude)
                .longitude(longitude)
                .build());
    }

    /** Danh mục tra theo slug vì slug mới là thứ frontend dùng để lọc, tên hiển thị có thể đổi. */
    private CategoryEntity ensureCategory(String categoryName, String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseGet(() -> categoryRepository.save(CategoryEntity.builder()
                        .categoryName(categoryName)
                        .slug(slug)
                        .build()));
    }

    /**
     * Nạp catalog mẫu từ {@code resources/data/products.json}.
     *
     * <p>Bỏ qua từng sản phẩm đã tồn tại (tra theo slug) thay vì bỏ qua cả mẻ, nhờ vậy thêm sản
     * phẩm mới vào file JSON rồi khởi động lại là nó tự vào DB, không phải xoá bảng làm lại.</p>
     */
    private void ensureCatalog() {
        for (ProductSeedData seed : readCatalogSeed()) {
            if (productRepository.existsBySlug(seed.slug())) {
                continue;
            }

            CategoryEntity category = categoryRepository.findBySlug(seed.categorySlug())
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_SERVER_ERROR,
                            "Seed data references unknown category slug: " + seed.categorySlug()));

            ProductEntity product = ProductEntity.builder()
                    .name(seed.name())
                    .slug(seed.slug())
                    .sku(seed.sku())
                    .price(seed.price())
                    .originalPrice(seed.originalPrice())
                    .description(seed.description())
                    .material(seed.material())
                    .careInstructions(seed.careInstructions())
                    .tag(seed.tag())
                    .thumbnail(seed.thumbnail())
                    .isSummer(Boolean.TRUE.equals(seed.isSummer()))
                    .isFeatured(Boolean.TRUE.equals(seed.isFeatured()))
                    .category(category)
                    .build();

            // Gán hai chiều rồi mới save: cascade ALL trên product sẽ ghi luôn variant và ảnh,
            // nhưng cột khoá ngoại product_id chỉ được điền nếu phía con cũng trỏ ngược lại.
            List<ProductVariantEntity> variants = new ArrayList<>();
            for (ProductSeedData.Variant variant : seed.variants()) {
                variants.add(ProductVariantEntity.builder()
                        .product(product)
                        .size(variant.size())
                        .stockQuantity(variant.stockQuantity())
                        .build());
            }
            product.getVariants().addAll(variants);

            List<ProductImageEntity> images = new ArrayList<>();
            for (int order = 0; order < seed.gallery().size(); order++) {
                images.add(ProductImageEntity.builder()
                        .product(product)
                        .imageUrl(seed.gallery().get(order))
                        .sortOrder(order)
                        .build());
            }
            product.getImages().addAll(images);

            productRepository.save(product);
        }
    }

    /** BST mẫu tạo qua Service để giữ cùng quy tắc HIDDEN -> ACTIVE với thao tác admin. */
    private void ensureSeedCollections() {
        for (CollectionSeedData seed : readSeedFile("data/collections.json", new TypeReference<List<CollectionSeedData>>() {
        })) {
            // Kể cả BST đã xoá mềm: không hồi sinh/ghi đè lựa chọn của admin khi khởi động lại.
            if (collectionRepository.existsBySlug(seed.slug())) {
                continue;
            }
            if (seed.productSkus() == null || seed.productSkus().isEmpty()) {
                throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "Collection seed requires at least one product SKU: " + seed.slug());
            }
            List<UUID> ids = seed.productSkus().stream().map(sku -> productRepository.findBySku(sku)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_SERVER_ERROR,
                            "Collection seed references unknown product SKU: " + sku)).getId()).toList();
            var created = collectionService.createCollection(new CollectionCreateRequest(
                    seed.collectionName(), seed.slug(), seed.coverImageUrl(), ids));
            collectionService.publishCollection(created.id(), new CollectionVersionRequest(created.version()));
        }
    }

    private List<ProductSeedData> readCatalogSeed() {
        return readSeedFile("data/products.json", new TypeReference<List<ProductSeedData>>() {
        });
    }

    private <T> List<T> readSeedFile(String path, TypeReference<List<T>> type) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return List.of();
        }
        try (InputStream input = resource.getInputStream()) {
            return jsonMapper.readValue(input, type);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Cannot read seed file " + path + ": " + ex.getMessage());
        }
    }

    /** Trả về role sẵn có, chỉ tạo mới khi chưa tồn tại — tên role là duy nhất nên tra theo tên là đủ. */
    private RoleEntity ensureRole(String roleName, String description) {
        return roleRepository.findByRoleNameIgnoreCase(roleName)
                .orElseGet(() -> roleRepository.save(RoleEntity.builder()
                        .roleName(roleName)
                        .description(description)
                        .build()));
    }

    /**
     * Chỉ tạo khi email chưa tồn tại. KHÔNG cập nhật tài khoản cũ: nếu người dùng đã đổi
     * mật khẩu rồi mà mỗi lần khởi động lại ghi đè về mặc định thì vừa phiền vừa nguy hiểm.
     */
    private void ensureAccount(String email, String rawPassword, RoleEntity role) {
        if (accountRepository.existsByEmailIgnoreCase(email)) {
            return;
        }

        accountRepository.save(AccountEntity.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .build());
    }
}
