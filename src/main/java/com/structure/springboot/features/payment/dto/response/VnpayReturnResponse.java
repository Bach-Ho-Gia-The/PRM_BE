package com.structure.springboot.features.payment.dto.response;

import com.structure.springboot.features.payment.enums.VnpayReturnState;

/**
 * Trang kết quả thanh toán mà <b>trình duyệt của khách</b> nhìn thấy khi VNPay redirect về.
 *
 * <h2>Nguyên tắc quan trọng nhất: trạng thái lấy từ DATABASE, không lấy từ URL</h2>
 *
 * <p>URL mà VNPay redirect về có chứa {@code vnp_ResponseCode}. Rất dễ — và rất sai — khi in thẳng
 * mã đó ra trang: "thanh toán thành công". Khách có thể gọi lại URL này bất cứ lúc nào, sửa
 * {@code vnp_ResponseCode} trong thanh địa chỉ, hoặc mở nó trước khi IPN về. Trang báo "thành
 * công" mà đơn thì chưa có tiền là lỗi nghiệp vụ nghiêm trọng, và nó xảy ra thường xuyên chứ không
 * phải ca hiếm.</p>
 *
 * <p>Chữ ký chỉ có <b>một</b> vai trò ở đây: chứng minh URL này do VNPay sinh ra, để không ai tự
 * dựng một URL giả rồi gửi cho khách. Nó <b>không</b> chứng minh đã thanh toán — chỉ IPN làm được
 * việc đó.</p>
 *
 * <h2>Vì sao {@code paidAtText} là String chứ không phải {@code Instant}</h2>
 *
 * <p>Lớp này chỉ lo hiển thị. Việc đổi {@code Instant} sang giờ Việt Nam cần
 * {@code VnpayProperties.zoneId()} — thứ thuộc về service. Đưa {@code Instant} vào đây là kéo cả
 * {@code ZoneId} theo, và người đọc class này lại phải tự hỏi "giờ này in ra theo múi nào".</p>
 *
 * @param state               trạng thái hiển thị, quyết định tiêu đề và câu mô tả
 * @param orderNumber         mã đơn, {@code null} nếu không tìm thấy
 * @param amountText          số tiền đã định dạng sẵn (ví dụ {@code 1.030.000 ₫}), {@code null} nếu chưa biết
 * @param paidAtText          thời điểm thanh toán đã đổi sang giờ Việt Nam, {@code null} nếu chưa trả tiền
 * @param txnRef              mã giao dịch của mình — để khách đọc cho shop khi cần hỗ trợ
 * @param gatewayResponseCode mã VNPay trả về, chỉ in ra như <b>thông tin tham khảo</b>, không dùng
 *                            để kết luận thành bại
 */
public record VnpayReturnResponse(
        VnpayReturnState state,
        String orderNumber,
        String amountText,
        String paidAtText,
        String txnRef,
        String gatewayResponseCode
) {

    // ── Nhà máy cho từng trạng thái. Gom ở đây để service không tự ghép chuỗi hiển thị. ──

    public static VnpayReturnResponse success(String orderNumber, String amountText,
                                          String paidAtText, String txnRef, String code) {
        return new VnpayReturnResponse(VnpayReturnState.SUCCESS, orderNumber, amountText, paidAtText, txnRef, code);
    }

    public static VnpayReturnResponse paidAfterCancel(String orderNumber, String amountText,
                                                  String paidAtText, String txnRef, String code) {
        return new VnpayReturnResponse(VnpayReturnState.PAID_AFTER_CANCEL, orderNumber, amountText, paidAtText,
                txnRef, code);
    }

    public static VnpayReturnResponse pendingConfirmation(String orderNumber, String amountText,
                                                      String txnRef, String code) {
        return new VnpayReturnResponse(VnpayReturnState.PENDING_CONFIRMATION, orderNumber, amountText, null,
                txnRef, code);
    }

    public static VnpayReturnResponse failed(String orderNumber, String amountText,
                                         String txnRef, String code) {
        return new VnpayReturnResponse(VnpayReturnState.FAILED, orderNumber, amountText, null, txnRef, code);
    }

    public static VnpayReturnResponse txnRefNotFound(String txnRef) {
        return new VnpayReturnResponse(VnpayReturnState.TXN_REF_NOT_FOUND, null, null, null, txnRef, null);
    }

    public static VnpayReturnResponse invalidLink() {
        return new VnpayReturnResponse(VnpayReturnState.INVALID_LINK, null, null, null, null, null);
    }

    public static VnpayReturnResponse systemError() {
        return new VnpayReturnResponse(VnpayReturnState.SYSTEM_ERROR, null, null, null, null, null);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Hiển thị
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Dựng trang HTML hoàn chỉnh.
     *
     * <p>Toàn bộ CSS viết inline trong thẻ {@code <style>}: trang này được mở từ điện thoại của
     * khách, thường trên mạng di động yếu. Một file CSS từ CDN có thể không tải được và khách nhìn
     * thấy trang trắng đúng vào lúc họ vừa trả tiền — thời điểm tệ nhất để nghi ngờ.</p>
     */
    public String toHtml() {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>
                  *{box-sizing:border-box;margin:0;padding:0}
                  body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Arial,sans-serif;
                       background:#f4f5f7;color:#1f2328;display:flex;align-items:center;
                       justify-content:center;min-height:100vh;padding:24px;line-height:1.5}
                  .card{background:#fff;width:100%%;max-width:420px;border-radius:16px;
                        padding:32px 24px;text-align:center;
                        box-shadow:0 1px 3px rgba(0,0,0,.08),0 8px 24px rgba(0,0,0,.06)}
                  .badge{width:64px;height:64px;border-radius:50%%;margin:0 auto 20px;
                         display:flex;align-items:center;justify-content:center;
                         font-size:32px;line-height:1;color:#fff}
                  h1{font-size:19px;font-weight:600;margin-bottom:10px}
                  p.msg{font-size:14px;color:#5b6169;margin-bottom:20px}
                  dl{text-align:left;border-top:1px solid #eceef1;padding-top:16px;font-size:13px}
                  dl div{display:flex;justify-content:space-between;gap:16px;padding:6px 0}
                  dt{color:#7a828b;flex:0 0 auto}
                  dd{font-weight:500;text-align:right;word-break:break-all}
                  .amount{font-size:15px;font-weight:600;color:#1f2328}
                  footer{margin-top:22px;font-size:12px;color:#9aa2ab}
                </style>
                </head>
                <body>
                <main class="card">
                  <div class="badge" style="%s">%s</div>
                  <h1>%s</h1>
                  <p class="msg">%s</p>
                  %s
                  <footer>%s</footer>
                </main>
                </body>
                </html>
                """.formatted(
                escape(title()),
                badgeBackground(),
                escape(badgeGlyph()),
                escape(title()),
                escape(message()),
                detailsBlock(),
                escape(footerNote()));
    }

    private String title() {
        return switch (state) {
            case SUCCESS -> "Thanh toán thành công";
            case PAID_AFTER_CANCEL -> "Đã nhận được thanh toán";
            case PENDING_CONFIRMATION -> "Đang xác nhận thanh toán";
            case FAILED -> "Thanh toán không thành công";
            case TXN_REF_NOT_FOUND -> "Không tìm thấy giao dịch";
            case INVALID_LINK -> "Liên kết không hợp lệ";
            case SYSTEM_ERROR -> "Không hiển thị được kết quả";
        };
    }

    private String message() {
        return switch (state) {
            case SUCCESS -> "Đơn hàng của bạn đã được thanh toán. Shop sẽ xác nhận và chuẩn bị hàng.";
            case PAID_AFTER_CANCEL ->
                    "Đơn hàng này đã được huỷ trước khi thanh toán hoàn tất. "
                            + "Chúng tôi đã ghi nhận khoản tiền và sẽ hoàn lại cho bạn.";
            case PENDING_CONFIRMATION ->
                    "Chúng tôi chưa nhận được xác nhận từ cổng thanh toán. "
                            + "Vui lòng không thanh toán lại — đơn hàng sẽ được cập nhật trong ít phút.";
            case FAILED -> "Giao dịch không được thực hiện. Bạn có thể thử thanh toán lại từ đơn hàng.";
            case TXN_REF_NOT_FOUND -> "Mã giao dịch này không tồn tại trong hệ thống của chúng tôi.";
            case INVALID_LINK ->
                    "Đường dẫn này không do cổng thanh toán tạo ra. "
                            + "Vui lòng mở lại đơn hàng trong ứng dụng để xem trạng thái thật.";
            case SYSTEM_ERROR ->
                    "Chúng tôi không hiển thị được kết quả lúc này. "
                            + "Đơn hàng của bạn vẫn an toàn — hãy mở ứng dụng để xem trạng thái.";
        };
    }

    private String badgeGlyph() {
        return switch (state) {
            case SUCCESS -> "✓";
            case PAID_AFTER_CANCEL -> "↺";
            case PENDING_CONFIRMATION -> "…";
            case FAILED, INVALID_LINK -> "×";
            case TXN_REF_NOT_FOUND -> "?";
            case SYSTEM_ERROR -> "!";
        };
    }

    private String badgeBackground() {
        return switch (state) {
            case SUCCESS -> "background:#16a34a";
            case PAID_AFTER_CANCEL -> "background:#2563eb";
            case PENDING_CONFIRMATION -> "background:#d97706";
            case FAILED, INVALID_LINK -> "background:#dc2626";
            case TXN_REF_NOT_FOUND -> "background:#6b7280";
            case SYSTEM_ERROR -> "background:#6b7280";
        };
    }

    /**
     * Khối chi tiết. Chỉ in những dòng thật sự có dữ liệu — in "Mã đơn: (trống)" tệ hơn là không in.
     */
    private String detailsBlock() {
        StringBuilder rows = new StringBuilder();
        appendRow(rows, "Mã đơn", orderNumber);
        appendRow(rows, "Số tiền", amountText);
        appendRow(rows, "Thời điểm thanh toán", paidAtText);
        appendRow(rows, "Mã giao dịch", txnRef);
        appendRow(rows, "Mã phản hồi", gatewayResponseCode);

        if (rows.isEmpty()) {
            return "";
        }
        return "<dl>" + rows + "</dl>";
    }

    private void appendRow(StringBuilder rows, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        boolean isAmount = "Số tiền".equals(label);
        rows.append("<div><dt>").append(escape(label)).append("</dt><dd")
                .append(isAmount ? " class=\"amount\"" : "")
                .append(">").append(escape(value)).append("</dd></div>");
    }

    private String footerNote() {
        if (state == VnpayReturnState.INVALID_LINK) {
            return "Không có thông tin nào được ghi nhận từ đường dẫn này.";
        }
        if (state == VnpayReturnState.SYSTEM_ERROR) {
            return "Sự cố hiển thị — không ảnh hưởng đến trạng thái đơn hàng.";
        }
        return "Cần hỗ trợ? Hãy đọc mã giao dịch ở trên cho shop.";
    }

    /**
     * Escape mọi giá trị động trước khi ghép vào HTML.
     *
     * <p><b>Vì sao bắt buộc:</b> {@code vnp_TxnRef}, {@code vnp_OrderInfo}, {@code vnp_ResponseCode}
     * đều là dữ liệu <b>từ ngoài</b> đi vào trang HTML. Không escape thì một URL dạng
     * {@code ...&vnp_ResponseCode=<script>...</script>} biến trang thanh toán của mình thành nơi
     * chạy mã của người khác. Dự án không dùng Thymeleaf nên không có auto-escape — phải tự làm.</p>
     *
     * <p>Escape cả {@code '} dù ta chỉ dùng nháy kép: nội dung có thể rơi vào thuộc tính, và người
     * sửa sau không phải nhớ chi tiết đó.</p>
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }
}
