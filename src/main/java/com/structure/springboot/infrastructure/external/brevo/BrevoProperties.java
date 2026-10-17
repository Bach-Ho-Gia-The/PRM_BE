package com.structure.springboot.infrastructure.external.brevo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Thông tin kết nối Brevo, đọc từ {@code brevo.*}.
 *
 * <p>Để ở tiền tố gốc {@code brevo} chứ không phải {@code app.brevo}: đây là cấu hình của một
 * dịch vụ bên ngoài, cùng loại với {@code spring.*} hay {@code springdoc.*}, không phải quy tắc
 * nghiệp vụ của ứng dụng.</p>
 */
@ConfigurationProperties(prefix = "brevo")
public record BrevoProperties(
        String apiKey,
        String senderEmail,
        String senderName
) {
    /**
     * {@code hasText} chứ không {@code != null} — cùng lý do với {@code AuthCookieProperties}:
     * {@code BREVO_SENDER_EMAIL=} để trống trong {@code .env} là chuỗi rỗng chứ không phải
     * thiếu biến, và Brevo trả 400 cho người gửi rỗng — một lỗi 502 chẳng gợi ý được gì.
     *
     * <p>{@code apiKey} cố ý KHÔNG có mặc định: không có khoá thì không gửi được mail thật, bịa
     * ra một giá trị giả chỉ làm lỗi hiện ra muộn hơn. {@code BrevoMailClient} kiểm tại chỗ gửi
     * và trả 503 kèm tên biến môi trường còn thiếu.</p>
     */
    public BrevoProperties {
        senderEmail = StringUtils.hasText(senderEmail) ? senderEmail : "no-reply@example.com";
        senderName = StringUtils.hasText(senderName) ? senderName : "System";
    }
}
