package com.structure.springboot.features.product.service;

import com.structure.springboot.common.response.PageResponse;
import com.structure.springboot.features.product.dto.request.ProductFilter;
import com.structure.springboot.features.product.dto.request.ProductRequest;
import com.structure.springboot.features.product.dto.request.ProductThumbnailRequest;
import com.structure.springboot.features.product.dto.request.ProductVariantRequest;
import com.structure.springboot.features.product.dto.request.ProductVariantUpdateRequest;
import com.structure.springboot.features.product.dto.request.ProductUpdateRequest;
import com.structure.springboot.features.product.dto.response.ProductDetailResponse;
import com.structure.springboot.features.product.dto.response.ProductResponse;
import com.structure.springboot.features.product.dto.response.ProductVariantResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    ProductDetailResponse createProduct(ProductRequest request);

    ProductDetailResponse updateProduct(UUID id, ProductUpdateRequest request);

    /** New images enter the gallery; the first one becomes the thumbnail if none exists. */
    ProductDetailResponse addImage(UUID id, MultipartFile file, boolean asThumbnail);

    ProductDetailResponse replaceImage(UUID id, UUID imageId, MultipartFile file);

    ProductDetailResponse removeImage(UUID id, UUID imageId);

    /** Replaces the current gallery thumbnail image, or adds one when it is external/missing. */
    ProductDetailResponse uploadThumbnail(UUID id, MultipartFile file);

    /** Selecting a gallery image does not delete the previously selected gallery asset. */
    ProductDetailResponse selectThumbnail(UUID id, ProductThumbnailRequest request);

    /**
     * Xoá MỀM: chỉ đổi trạng thái sang {@code DELETED}.
     *
     * <p>Không xoá cứng vì {@code order_items} trỏ vào sản phẩm — xoá thật là phá lịch sử đơn hàng.
     * Gọi lại trên sản phẩm đã xoá thì trả 404 như thể nó không còn tồn tại.</p>
     */
    void deleteProduct(UUID id);

    /** Thêm một size. Mỗi sản phẩm tối đa một dòng tồn kho cho mỗi size. */
    ProductDetailResponse addVariant(UUID id, ProductVariantRequest request);
    /** Sửa tồn kho; {@code expectedVersion} chặn hai admin ghi đè nhau. */
    ProductDetailResponse updateVariant(UUID id, UUID variantId, ProductVariantUpdateRequest request);
    /**
     * Gỡ một size khỏi sản phẩm (xoá cứng).
     *
     * <p>Chặn khi size còn nằm trong đơn chưa kết thúc: đơn đó bị huỷ sẽ phải hoàn kho về chính
     * dòng này. Đơn đã xong/đã huỷ thì không chặn vì {@code order_items} đã giữ bản chụp riêng.</p>
     */
    ProductDetailResponse removeVariant(UUID id, UUID variantId);
    /** Liệt kê tồn kho cho màn quản trị, gồm cả size đang hết hàng. */
    List<ProductVariantResponse> getAdminVariants(UUID id);
    /** Admin thấy cả Product HIDDEN, không thấy DELETED. */
    ProductDetailResponse getAdminProductById(UUID id);

    PageResponse<ProductResponse> getAdminProducts(Pageable pageable);

    /** Chi tiết công khai: Product HIDDEN/DELETED trả 404 với mọi vai trò. */
    ProductDetailResponse getProductById(UUID id);

    ProductDetailResponse getProductBySlug(String slug);

    /** Danh sách công khai; các filter tuỳ chọn được áp trước phân trang. */
    PageResponse<ProductResponse> getAllProducts(ProductFilter filter, Pageable pageable);
}
