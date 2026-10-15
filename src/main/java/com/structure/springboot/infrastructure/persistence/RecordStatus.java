package com.structure.springboot.infrastructure.persistence;

/**
 * Vòng đời của một bản ghi nghiệp vụ dùng chung cho mọi bảng có {@link SoftDeletableEntity}.
 *
 * <p>Gộp "ẩn" và "đã xoá" vào một cột thay vì dùng cờ {@code isDeleted} riêng, vì shop cần ba
 * trạng thái chứ không phải hai: còn bán, tạm ẩn (hết mùa, chờ ảnh mới) và đã xoá. Một cờ boolean
 * sẽ phải cõng thêm cột thứ hai cho việc ẩn, rồi hai cột đó lại có thể mâu thuẫn nhau.</p>
 */
public enum RecordStatus {

    /** Hoạt động bình thường, khách nhìn thấy. */
    ACTIVE,

    /** Admin chủ động ẩn khỏi cửa hàng nhưng vẫn quản lý được. Khách gọi API sẽ không thấy. */
    HIDDEN,

    /** Đã xoá mềm. Không ai đọc được qua API, chỉ còn tồn tại để đơn hàng cũ không bị mồ côi. */
    DELETED
}
