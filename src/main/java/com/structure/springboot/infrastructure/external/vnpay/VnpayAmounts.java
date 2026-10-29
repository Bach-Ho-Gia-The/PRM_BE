package com.structure.springboot.infrastructure.external.vnpay;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

/**
 * Đổi số tiền giữa đơn vị hiển thị của dự án và đơn vị của VNPay.
 *
 * <h2>Vì sao cần một chỗ duy nhất</h2>
 *
 * <p>VNPay tính bằng <b>đồng × 100</b>: gửi {@code 2988000} thì cổng hiểu là hai mươi chín nghìn tám
 * trăm tám mươi đồng, không phải gần ba triệu. Phép nhân này xuất hiện ở hai chiều ngược nhau — lúc
 * <b>gửi</b> tham số đi (dựng link thanh toán) và lúc <b>đối chiếu</b> tham số về (kiểm tra IPN và
 * {@code querydr}).</p>
 *
 * <p>Hai chiều dùng chung một phép tính là điều bắt buộc: nếu một bên nhân 100 còn bên kia không, mọi
 * giao dịch đều bị coi là lệch tiền và bị từ chối — mà triệu chứng thì giống hệt "khách chuyển sai
 * số tiền". Cùng một hàm, cùng một hệ số, thì lỗi đó không thể xảy ra.</p>
 *
 * <h2>Hai lối vào, một phép tính</h2>
 *
 * <p>Hai nơi dùng có <b>cách xử lý đầu vào sai khác hẳn nhau</b>, và đó là chủ ý chứ không phải thiếu
 * nhất quán:</p>
 *
 * <ul>
 *   <li>Dựng link thanh toán: số tiền sai là lỗi lập trình và phải <b>ném ra</b> — không được gửi
 *       một link sai sang cổng.</li>
 *   <li>Đối chiếu số tiền: số tiền không tính được thì kết luận là <b>"không khớp"</b> — ném ra
 *       trong luồng đối soát sẽ biến một câu trả lời lạ thành sự cố, và tệ hơn, có thể bị bắt nhầm
 *       thành ca khác.</li>
 * </ul>
 *
 * <p>Nên có hai lối vào, nhưng <b>chỉ một</b> phép tính bên trong. Hai lối vào không tự nhân số.</p>
 */
public final class VnpayAmounts {

    /** VNPay dùng đơn vị nhỏ hơn đồng 100 lần. */
    private static final int GATEWAY_SCALE = 2;

    /**
     * Độ dài tối đa của trường {@code vnp_Amount} theo spec. Vượt là VNPay cắt hoặc từ chối, và cắt
     * thì số tiền thành một con số khác — nguy hiểm hơn từ chối.
     */
    private static final int MAX_FIELD_LENGTH = 12;

    private VnpayAmounts() {
    }

    /**
     * Phép tính duy nhất: nhân 100, <b>không</b> làm tròn.
     *
     * <p>Cố ý không làm tròn: số tiền lẻ hơn hai chữ số thập phân là lỗi dữ liệu, và làm tròn nó sẽ
     * biến một khoản sai thành một khoản trông hợp lệ. {@link BigDecimal#toBigIntegerExact} ném
     * {@link ArithmeticException} đúng trong trường hợp đó.</p>
     */
    private static BigInteger scale(BigDecimal amount) {
        return amount.movePointRight(GATEWAY_SCALE).toBigIntegerExact();
    }

    private static boolean isUsable(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }

    /**
     * Đổi số tiền sang đơn vị VNPay, <b>ném ra</b> nếu không đổi được.
     *
     * <p>Dùng khi dựng tham số gửi đi: số tiền không hợp lệ thì không được có link thanh toán.</p>
     *
     * @throws IllegalArgumentException khi số tiền null hoặc không dương
     * @throws ArithmeticException      khi số tiền có hơn hai chữ số thập phân
     */
    public static BigInteger requireGatewayAmount(BigDecimal amount) {
        if (!isUsable(amount)) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        return scale(amount);
    }

    /**
     * Đổi số tiền sang đơn vị VNPay, <b>trả rỗng</b> nếu không đổi được.
     *
     * <p>Dùng khi đối chiếu: không tính được nghĩa là "không khớp", không phải sự cố.</p>
     */
    public static Optional<BigInteger> findGatewayAmount(BigDecimal amount) {
        if (!isUsable(amount)) {
            return Optional.empty();
        }
        try {
            return Optional.of(scale(amount));
        } catch (ArithmeticException exception) {
            return Optional.empty();
        }
    }

    /** Số tiền đã đổi có vừa trường {@code vnp_Amount} không. */
    public static boolean fitsField(BigInteger gatewayAmount) {
        return gatewayAmount.toString().length() <= MAX_FIELD_LENGTH;
    }
}
