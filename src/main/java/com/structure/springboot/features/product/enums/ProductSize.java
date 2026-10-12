package com.structure.springboot.features.product.enums;

/**
 * Size bán ra. Để enum thay vì chuỗi tự do để không phát sinh "M", "m", " M " thành ba size khác
 * nhau trong kho. Thêm size mới (XL, XXL) chỉ cần thêm một hằng ở đây.
 */
public enum ProductSize {
    S,
    M,
    L
}
