package com.structure.springboot.features.product.service;

import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.product.dto.request.ProductFilter;
import com.structure.springboot.features.product.dto.request.ProductRequest;
import com.structure.springboot.features.product.dto.request.ProductUpdateRequest;
import com.structure.springboot.features.product.dto.response.ProductDetailResponse;
import com.structure.springboot.features.product.dto.response.ProductResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductService {

    ProductDetailResponse createProduct(ProductRequest request);

    ProductDetailResponse updateProduct(UUID id, ProductUpdateRequest request);

    /**
     * Xoá MỀM: chỉ đổi trạng thái sang {@code DELETED}.
     *
     * <p>Không xoá cứng vì {@code order_items} trỏ vào sản phẩm — xoá thật là phá lịch sử đơn hàng.
     * Gọi lại trên sản phẩm đã xoá thì trả 404 như thể nó không còn tồn tại.</p>
     */
    void deleteProduct(UUID id);

    /** Chi tiết cho khách: sản phẩm đang ẩn hoặc đã xoá đều trả 404. */
    ProductDetailResponse getProductById(UUID id);

    /** Đường vào của trang {@code /san-pham/{slug}}. */
    ProductDetailResponse getProductBySlug(String slug);

    /** Danh sách có lọc + phân trang. Chỉ trả về sản phẩm đang bán. */
    PageResponse<ProductResponse> getAllProducts(ProductFilter filter, Pageable pageable);
}
