package com.structure.springboot.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Bản ghi KHÔNG được xoá cứng: xoá chỉ là đổi {@link RecordStatus} sang {@code DELETED}.
 *
 * <p><b>Vì sao bắt buộc với sản phẩm và danh mục:</b> {@code order_items} trỏ vào sản phẩm. Xoá
 * cứng một sản phẩm đã từng được mua là phá luôn lịch sử đơn hàng — hoặc lỗi khoá ngoại, hoặc
 * (tệ hơn) mất sạch dữ liệu đơn cũ.</p>
 *
 * <p><b>Vì sao là lớp riêng chứ không nhét thẳng vào {@code BaseEntity}:</b> không phải bảng nào
 * cũng cần xoá mềm, và {@code AccountEntity} đã có sẵn cột {@code status} mang ý nghĩa khác
 * (trạng thái đăng nhập: ACTIVE/DISABLED/LOCKED). Dồn vào {@code BaseEntity} là hai cột {@code status}
 * đụng tên nhau trên cùng một bảng.</p>
 */
@MappedSuperclass
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public abstract class SoftDeletableEntity extends BaseEntity {

    /**
     * Để nullable vì {@code ddl-auto: update} không thêm được cột NOT NULL vào bảng đã có dữ liệu
     * (cùng lý do với {@code AccountEntity.status}). Hàng cũ mang null và được coi là ACTIVE.
     * Sau khi chạy {@code UPDATE products SET status = 'ACTIVE' WHERE status IS NULL;} thì mới
     * siết được {@code nullable = false}.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private RecordStatus status = RecordStatus.ACTIVE;
}
