package com.structure.springboot.infrastructure.external.brevo;

import com.structure.springboot.common.exception.ApiException;
import com.structure.springboot.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Gửi email giao dịch qua REST API của Brevo.
 *
 * <p><b>Vì sao không thêm {@code spring-boot-starter-mail}:</b> {@code RestClient} đã nằm sẵn
 * trong {@code spring-web} nên gọi thẳng HTTP API là xong, không phải nạp thêm thư viện nào.</p>
 *
 * <p><b>Body gửi đi dùng {@code Map} chứ không phải record DTO:</b> Brevo bắt buộc JSON lồng
 * nhau ({@code sender} là object, {@code to} là mảng object) trong khi khuôn DTO của dự án quy
 * định record phẳng. Đây là payload gọi ra ngoài, không phải hợp đồng API của dự án.</p>
 *
 * <p><b>Hai nhóm mail, hai cách xử lý lỗi khác hẳn nhau:</b></p>
 * <ul>
 *   <li><b>Mail chặn luồng</b> (OTP) — gửi đồng bộ, lỗi thì ném lên để người dùng biết mà thử
 *       lại; không có mail thì họ đứng chết ở bước sau.</li>
 *   <li><b>Mail báo tin</b> (đổi mật khẩu) — gửi bất đồng bộ và nuốt lỗi; nghiệp vụ đã xong
 *       xuôi rồi, không thể vì Brevo rớt mà báo thất bại hay quay ngược thao tác đã thành công.</li>
 * </ul>
 *
 * <p><b>Ràng buộc khi viết HTML cho email:</b> client thư (Gmail, Outlook) cắt bỏ JavaScript,
 * phần lớn bỏ qua thẻ {@code <style>} và không tải CSS ngoài. Vì vậy bố cục phải dựng bằng
 * {@code <table>} và mọi định dạng phải viết inline — đó là lý do mã HTML dưới đây trông rườm
 * rà chứ không phải vì chưa dọn.</p>
 */
@Component
@RequiredArgsConstructor
public class BrevoMailClient {

    private final BrevoProperties properties;

    // Khởi tạo tại chỗ: RestClient không có trạng thái, dùng lại được cho mọi request và
    // Lombok bỏ qua field final đã gán sẵn nên không lọt vào constructor.
    private final RestClient restClient = RestClient.create("https://api.brevo.com/v3");

    public void sendOtpEmail(String toEmail, String otp, int ttlSeconds) {
        send(toEmail, "Mã xác minh đặt lại mật khẩu", layout(
                "Đặt lại mật khẩu",
                "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn. "
                        + "Nhập mã bên dưới để tiếp tục.",
                codeBlock(otp),
                "Mã có hiệu lực trong " + ttlSeconds + " giây.",
                "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này — "
                        + "mật khẩu hiện tại của bạn vẫn an toàn."));
    }

    public void sendRegistrationOtpEmail(String toEmail, String otp, int ttlSeconds) {
        send(toEmail, "Xác minh email đăng ký", layout(
                "Xác minh email của bạn",
                "Cảm ơn bạn đã đăng ký. Nhập mã bên dưới để hoàn tất việc tạo tài khoản.",
                codeBlock(otp),
                "Mã có hiệu lực trong " + Math.max(1, ttlSeconds / 60) + " phút. "
                        + "Quá hạn mà chưa xác minh thì đăng ký sẽ bị huỷ và bạn cần khai báo lại.",
                "Nếu bạn không đăng ký tài khoản nào, hãy bỏ qua email này — "
                        + "chưa có tài khoản nào được tạo bằng địa chỉ của bạn."));
    }

    /**
     * Báo cho chủ tài khoản biết mật khẩu vừa bị đổi — nếu không phải họ làm thì đây là tín hiệu
     * duy nhất để họ kịp phản ứng.
     *
     * <p><b>{@code @Async} chứ không gọi thẳng:</b> hàm gọi nó nằm trong {@code @Transactional},
     * nhét một lời gọi mạng vào đó là giữ connection database suốt thời gian đi mạng, và mail
     * hỏng có thể kéo rollback cả việc đổi mật khẩu đã thành công.</p>
     *
     * <p><b>Nuốt lỗi là cố ý:</b> chạy ở thread khác nên không ai bắt được ngoại lệ ném ra từ
     * đây; để nó bay lên thì chỉ tạo ra một stacktrace vô chủ chứ không giúp được gì.</p>
     */
    @Async
    public void sendPasswordChangedEmail(String toEmail) {
        try {
            send(toEmail, "Mật khẩu của bạn vừa được thay đổi", layout(
                    "Mật khẩu đã được thay đổi",
                    "Mật khẩu của tài khoản " + toEmail + " vừa được cập nhật thành công.",
                    noticeBlock("Mọi thiết bị đang đăng nhập đã bị đăng xuất. "
                            + "Bạn cần đăng nhập lại bằng mật khẩu mới."),
                    "Nếu chính bạn vừa thực hiện thao tác này, bạn không cần làm gì thêm.",
                    "Nếu KHÔNG phải bạn, hãy dùng chức năng Quên mật khẩu để giành lại quyền "
                            + "kiểm soát tài khoản ngay lập tức."));
        } catch (RuntimeException ignored) {
            // Xem javadoc: mail báo tin không được phép làm hỏng nghiệp vụ đã hoàn tất.
        }
    }

    /**
     * Xác nhận đơn hàng vừa đặt.
     *
     * <p>Cùng nhóm "mail báo tin" với {@link #sendPasswordChangedEmail}: {@code @Async} + nuốt
     * lỗi. Đơn đã nằm trong database rồi, Brevo rớt không được phép làm hỏng việc đặt hàng.</p>
     *
     * <p><b>Nhận giá trị rời chứ không nhận {@code OrderEntity}:</b> chạy ở thread khác nên
     * phiên Hibernate đã đóng, chạm vào quan hệ lazy là {@code LazyInitializationException}.</p>
     *
     * <p>⚠️ Tài khoản Brevo Free dùng chung 300 mail/ngày cho CẢ OTP đăng ký lẫn mail này. Hết
     * quota thì mail này im lặng bỏ qua, còn {@code /auth/register} sẽ trả 503.</p>
     */
    @Async
    public void sendOrderConfirmationEmail(String toEmail, String orderNumber, String receiverName,
                                           BigDecimal total, String address) {
        try {
            send(toEmail, "Xác nhận đơn hàng " + orderNumber, layout(
                    "Cảm ơn bạn đã đặt hàng",
                    "Chào " + receiverName + ", chúng tôi đã nhận được đơn hàng của bạn "
                            + "và đang chờ xác nhận.",
                    orderBlock(orderNumber, total, address),
                    "Bạn sẽ nhận được thông báo trong ứng dụng mỗi khi đơn hàng đổi trạng thái.",
                    "Đơn còn ở trạng thái Chờ xác nhận thì bạn vẫn tự huỷ được trong mục "
                            + "Đơn hàng của tôi."));
        } catch (RuntimeException ignored) {
            // Xem javadoc: mail báo tin không được phép làm hỏng nghiệp vụ đã hoàn tất.
        }
    }

    /** Khối tóm tắt đơn: mã đơn, tổng tiền, nơi nhận. */
    private String orderBlock(String orderNumber, BigDecimal total, String address) {
        return """
                <div style="background-color:#f9fafb;border:1px solid #e5e7eb;border-radius:12px;
                            padding:18px 20px;">
                  <div style="font-size:13px;color:#6b7280;">Mã đơn hàng</div>
                  <div style="font-size:20px;font-weight:700;letter-spacing:1px;color:#111827;
                              margin-bottom:14px;">%s</div>
                  <div style="font-size:13px;color:#6b7280;">Tổng thanh toán</div>
                  <div style="font-size:18px;font-weight:700;color:#111827;margin-bottom:14px;">%s</div>
                  <div style="font-size:13px;color:#6b7280;">Giao đến</div>
                  <div style="font-size:14px;line-height:21px;color:#111827;">%s</div>
                </div>
                """.formatted(orderNumber, formatMoney(total), address);
    }

    /** "1.250.000 d" — dấu chấm phân nhóm theo cách viết tiền Việt. */
    private String formatMoney(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,###");
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        format.setDecimalFormatSymbols(symbols);
        return format.format(amount) + " đ";
    }

    /** Hộp mã lớn, giãn chữ cho dễ đọc và dễ chép tay. */
    private String codeBlock(String otp) {
        return """
                <div style="background-color:#f9fafb;border:1px dashed #d1d5db;border-radius:12px;
                            padding:20px;text-align:center;">
                  <span style="font-size:34px;font-weight:700;letter-spacing:10px;color:#111827;">%s</span>
                </div>
                """.formatted(otp);
    }

    /** Khối nhấn mạnh cho mail báo tin — viền trái màu hổ phách, không có mã để chép. */
    private String noticeBlock(String text) {
        return """
                <div style="background-color:#fffbeb;border-left:4px solid #f59e0b;border-radius:8px;
                            padding:16px 18px;">
                  <span style="font-size:14px;line-height:22px;color:#92400e;">%s</span>
                </div>
                """.formatted(text);
    }

    /**
     * Khung HTML dùng chung cho mọi email của hệ thống.
     *
     * <p>Gom về một chỗ để các loại mail không bao giờ lệch nhau về nhận diện thương hiệu; muốn
     * đổi màu hay logo thì sửa đúng hàm này. Phần ruột giữa truyền vào dưới dạng HTML để mail
     * OTP dùng hộp mã còn mail báo tin dùng khối cảnh báo, mà vẫn chung một cái khung.</p>
     *
     * <p>Lưu ý cú pháp: {@code 100%%} trong chuỗi là <b>cố ý</b> — {@code formatted()} coi
     * {@code %} là ký tự điều khiển nên phải nhân đôi, viết một dấu là ném
     * {@code UnknownFormatConversionException} ngay lúc chạy.</p>
     */
    private String layout(String heading, String intro, String highlightHtml,
                          String note, String footerNote) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <body style="margin:0;padding:0;background-color:#f4f4f5;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                       style="background-color:#f4f4f5;padding:32px 12px;">
                  <tr><td align="center">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                           style="max-width:480px;background-color:#ffffff;border-radius:14px;
                                  overflow:hidden;font-family:'Segoe UI',Arial,Helvetica,sans-serif;">

                      <tr><td style="background-color:#111827;padding:22px 32px;">
                        <span style="color:#ffffff;font-size:18px;font-weight:700;letter-spacing:2px;">%s</span>
                      </td></tr>

                      <tr><td style="padding:32px 32px 28px 32px;">
                        <div style="font-size:19px;font-weight:600;color:#111827;margin-bottom:10px;">%s</div>
                        <div style="font-size:14px;line-height:23px;color:#4b5563;margin-bottom:26px;">%s</div>
                        %s
                        <div style="font-size:13px;line-height:21px;color:#6b7280;margin-top:22px;">%s</div>
                        <div style="font-size:13px;line-height:21px;color:#6b7280;margin-top:12px;">%s</div>
                      </td></tr>

                      <tr><td style="background-color:#f9fafb;border-top:1px solid #e5e7eb;padding:16px 32px;">
                        <span style="font-size:12px;color:#9ca3af;">
                          Email tự động, vui lòng không trả lời. Tuyệt đối không chia sẻ mã này cho bất kỳ ai.
                        </span>
                      </td></tr>

                    </table>
                  </td></tr>
                </table>
                </body>
                </html>
                """.formatted(properties.senderName(), heading, intro, highlightHtml, note, footerNote);
    }

    private void send(String toEmail, String subject, String htmlContent) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Email service is not configured. Please set BREVO_API_KEY.");
        }

        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", properties.senderName(), "email", properties.senderEmail()),
                "to", List.of(Map.of("email", toEmail)),
                "subject", subject,
                "htmlContent", htmlContent
        );

        try {
            restClient.post()
                    .uri("/smtp/email")
                    .header("api-key", properties.apiKey())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException ex) {
            // Không nuốt lỗi: báo 502 để người dùng biết mà thử lại, thay vì im lặng rồi ngồi
            // chờ một email không bao giờ tới. (Cố ý không log — xem quy ước bỏ log của dự án.)
            throw new ApiException(ErrorCode.BAD_GATEWAY, "Could not send the verification email");
        }
    }
}
