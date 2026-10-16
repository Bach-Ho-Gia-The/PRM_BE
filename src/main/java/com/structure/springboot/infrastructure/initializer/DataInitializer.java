package com.structure.springboot.infrastructure.initializer;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.features.category.repository.CategoryRepository;
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

/**
 * Tạo sẵn dữ liệu tối thiểu để dự án chạy được ngay sau khi clone: hai role ADMIN/USER
 * và hai tài khoản tương ứng.
 *
 * <p><b>Idempotent</b> — mỗi lần khởi động đều kiểm tra tồn tại trước khi ghi, nên chạy lại
 * bao nhiêu lần cũng không sinh dữ liệu trùng và không ghi đè mật khẩu người dùng đã đổi.</p>
 *
 * <p>Tắt bằng {@code app.data-init.enabled=false} khi deploy thật.</p>
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
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
                    .collection(seed.collection())
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

    private List<ProductSeedData> readCatalogSeed() {
        ClassPathResource resource = new ClassPathResource("data/products.json");
        if (!resource.exists()) {
            return List.of();
        }

        try (InputStream input = resource.getInputStream()) {
            return jsonMapper.readValue(input, new TypeReference<List<ProductSeedData>>() {
            });
        } catch (IOException ex) {
            // Hỏng file seed thì để ứng dụng chết ngay lúc khởi động, hơn là chạy với catalog rỗng
            // rồi cả nhóm ngồi đoán vì sao không có sản phẩm nào.
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Cannot read catalog seed file data/products.json: " + ex.getMessage());
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
