package com.structure.springboot.features.order.enums;

/**
 * Hình thức thanh toán.
 *
 * <p>Web gọi là {@code cod}/{@code qr}, mobile gọi là {@code COD}/{@code BANKING} — hai app đặt
 * tên khác nhau cho cùng một thứ. Backend chốt một bộ duy nhất, FE tự hiển thị theo ý mình.</p>
 */
public enum PaymentMethod {
    COD,
    BANK_TRANSFER
}
