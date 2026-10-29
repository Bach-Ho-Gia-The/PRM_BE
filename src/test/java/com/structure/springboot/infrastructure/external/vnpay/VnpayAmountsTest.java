package com.structure.springboot.infrastructure.external.vnpay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phép đổi đơn vị tiền — chỗ duy nhất trong hệ biết VNPay dùng đơn vị nhỏ hơn đồng 100 lần.
 *
 * <p>Test ở đây khoá đúng <b>hệ số</b>, vì sai hệ số không làm hỏng dữ liệu mà làm sai tiền: gửi
 * thiếu 100 lần thì khách trả một đồng cho đơn hai triệu, và nếu VNPay chấp nhận thì IPN sẽ bị từ
 * chối với lý do "số tiền không khớp" — triệu chứng giống hệt khách chuyển khoản sai.</p>
 */
class VnpayAmountsTest {

    @Test
    @DisplayName("nhân đúng 100: 2.988.000,00 ⇒ 298800000")
    void theFactorIsOneHundred() {
        assertThat(VnpayAmounts.requireGatewayAmount(new BigDecimal("2988000.00")))
                .isEqualTo(new BigInteger("298800000"));

        assertThat(VnpayAmounts.requireGatewayAmount(new BigDecimal("1")))
                .isEqualTo(new BigInteger("100"));

        assertThat(VnpayAmounts.requireGatewayAmount(new BigDecimal("30000.50")))
                .isEqualTo(new BigInteger("3000050"));
    }

    @Test
    @DisplayName("không làm tròn: số tiền lẻ quá hai chữ số ⇒ ném, không âm thầm làm tròn")
    void amountsWithTooManyDecimalsAreRejected() {
        assertThatThrownBy(() -> VnpayAmounts.requireGatewayAmount(new BigDecimal("100.005")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("số tiền không dương ⇒ lối vào 'bắt buộc' ném")
    void nonPositiveAmountsThrowOnTheRequiredPath() {
        assertThatThrownBy(() -> VnpayAmounts.requireGatewayAmount(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> VnpayAmounts.requireGatewayAmount(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> VnpayAmounts.requireGatewayAmount(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("số tiền không dùng được ⇒ lối vào 'đối chiếu' trả rỗng, KHÔNG ném")
    void theLookupPathReturnsEmptyInsteadOfThrowing() {
        assertThat(VnpayAmounts.findGatewayAmount(null)).isEmpty();
        assertThat(VnpayAmounts.findGatewayAmount(BigDecimal.ZERO)).isEmpty();
        assertThat(VnpayAmounts.findGatewayAmount(new BigDecimal("-5"))).isEmpty();
        assertThat(VnpayAmounts.findGatewayAmount(new BigDecimal("1.234"))).isEmpty();
    }

    @Test
    @DisplayName("lối vào 'đối chiếu' trả đúng hệ số khi hợp lệ")
    void theLookupPathUsesTheSameFactor() {
        Optional<BigInteger> value = VnpayAmounts.findGatewayAmount(new BigDecimal("2988000.00"));

        assertThat(value).contains(new BigInteger("298800000"));
    }

    @Test
    @DisplayName("hai lối vào dùng CÙNG một phép tính — nếu lệch hệ số, test này đỏ")
    void bothEntryPointsAgree() {
        BigDecimal amount = new BigDecimal("1234567.89");

        Optional<BigInteger> lookup = VnpayAmounts.findGatewayAmount(amount);
        BigInteger required = VnpayAmounts.requireGatewayAmount(amount);

        assertThat(lookup).contains(required);
    }

    @Test
    @DisplayName("trường vnp_Amount tối đa 12 chữ số")
    void theFieldLengthIsEnforced() {
        assertThat(VnpayAmounts.fitsField(new BigInteger("999999999999"))).isTrue();  // 12 chữ số
        assertThat(VnpayAmounts.fitsField(new BigInteger("1000000000000"))).isFalse(); // 13 chữ số
    }

    @Test
    @DisplayName("đơn COD và đơn thường dùng chung: chỉ số tiền quyết định, không phụ thuộc phương thức")
    void theConversionDoesNotDependOnPaymentMethod() {
        assertThat(VnpayAmounts.requireGatewayAmount(new BigDecimal("500000")))
                .isEqualTo(new BigInteger("50000000"));
    }
}
