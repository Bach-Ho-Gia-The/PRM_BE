package com.structure.springboot.features.store.entity;

import com.structure.springboot.infrastructure.persistence.RecordStatus;
import com.structure.springboot.infrastructure.persistence.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Một chi nhánh cửa hàng, dùng cho màn bản đồ.
 *
 * <p>Toạ độ để {@code Double} chứ không {@code BigDecimal}: GPS không cần chính xác tuyệt đối như
 * tiền, và {@code Double} cắm thẳng vào {@code LatLng} của thư viện bản đồ không phải đổi kiểu.</p>
 */
@Entity
@Table(
        name = "stores",
        indexes = @Index(name = "idx_stores_status", columnList = "status")
)
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class StoreEntity extends SoftDeletableEntity {

    @Column(name = "store_name", nullable = false, length = 150)
    private String storeName;

    @Column(name = "address", nullable = false, length = 300)
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    /** Chuỗi tự do ("08:00 - 22:00"). Tách giờ mở/đóng thành bảng riêng là quá mức cần thiết. */
    @Column(name = "opening_hours", length = 100)
    private String openingHours;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * Khách có nhìn thấy chi nhánh này không.
     *
     * <p>Gom về đây theo đúng tiền lệ {@code ProductEntity.isVisible()}: điều kiện
     * "null coi như ACTIVE" mà chép ra từng service thì sớm muộn có chỗ quên vế {@code null}.</p>
     */
    public boolean isVisible() {
        return getStatus() == null || getStatus() == RecordStatus.ACTIVE;
    }
}
