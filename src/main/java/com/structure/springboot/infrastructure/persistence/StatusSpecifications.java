package com.structure.springboot.infrastructure.persistence;

import org.springframework.data.jpa.domain.Specification;

/**
 * Điều kiện lọc theo {@link RecordStatus} dùng chung cho mọi bảng xoá mềm.
 *
 * <p><b>Vì sao luôn phải cõng thêm {@code IS NULL}:</b> cột {@code status} để nullable (xem
 * {@link SoftDeletableEntity}), mà trong SQL thì {@code NULL <> 'DELETED'} cho ra {@code NULL} chứ
 * không phải {@code TRUE} — nghĩa là mọi hàng dữ liệu cũ sẽ biến mất khỏi kết quả. Gói sẵn ở đây
 * một lần để không chỗ nào quên.</p>
 */
public final class StatusSpecifications {

    private StatusSpecifications() {
    }

    /** Khách nhìn thấy: chỉ bản ghi đang bán. Bỏ qua cả HIDDEN lẫn DELETED. */
    public static <T extends SoftDeletableEntity> Specification<T> visible() {
        return (root, query, cb) -> cb.or(
                cb.isNull(root.get("status")),
                cb.equal(root.get("status"), RecordStatus.ACTIVE)
        );
    }

    /** Admin nhìn thấy: mọi bản ghi chưa xoá, kể cả đang ẩn. */
    public static <T extends SoftDeletableEntity> Specification<T> notDeleted() {
        return (root, query, cb) -> cb.or(
                cb.isNull(root.get("status")),
                cb.notEqual(root.get("status"), RecordStatus.DELETED)
        );
    }
}
