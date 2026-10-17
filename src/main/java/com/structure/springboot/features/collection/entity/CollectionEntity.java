package com.structure.springboot.features.collection.entity;

import com.structure.springboot.infrastructure.persistence.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnDefault;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Bộ sưu tập do admin tuyển chọn. Phân loại Áo/Quần vẫn thuộc Category của từng Product;
 * quan hệ nhiều-nhiều với Product được lưu ở {@link CollectionProductEntity}.
 *
 * <p>Khi tạo BST, service phải gán trạng thái HIDDEN trước khi lưu. Giá trị mặc định ACTIVE ở
 * {@link SoftDeletableEntity} phục vụ các module hiện có, không phải quy tắc đăng tải BST.</p>
 */
@Entity
@Table(name = "collections")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class CollectionEntity extends SoftDeletableEntity {

    @Column(name = "collection_name", nullable = false, length = 100)
    private String collectionName;

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    /** URL ảnh bìa có thể để trống; upload file là luồng riêng của admin. */
    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    /** Null for external/seed images; never included in the response. */
    @Column(name = "cover_image_public_id", length = 500)
    private String coverImagePublicId;

    /** Chặn màn hình quản trị cũ ghi đè thay đổi mới, kể cả khi chỉ sửa sản phẩm liên kết. */
    @Version
    @ColumnDefault("0")
    @Column(name = "version", nullable = false)
    private Long version;
}
