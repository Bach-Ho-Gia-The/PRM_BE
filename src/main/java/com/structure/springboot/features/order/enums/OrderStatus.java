package com.structure.springboot.features.order.enums;

/**
 * Vòng đời đơn hàng. Giá trị khớp đúng 5 bước mà frontend web đang vẽ trên thanh tiến trình
 * tra cứu đơn, nên FE chỉ việc map sang nhãn tiếng Việt chứ không phải quy đổi gì thêm.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPING,
    COMPLETED,
    CANCELLED
}
