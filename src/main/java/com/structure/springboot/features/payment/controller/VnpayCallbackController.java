package com.structure.springboot.features.payment.controller;

import com.structure.springboot.features.payment.dto.response.VnpayIpnAcknowledgement;
import com.structure.springboot.features.payment.dto.response.VnpayReturnResponse;
import com.structure.springboot.features.payment.service.VnpayCallbackService;
import com.structure.springboot.features.payment.service.VnpayReturnService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Endpoint công khai cho VNPay gọi vào.
 *
 * <h2>Vì sao nằm ở {@code /vnpay} chứ không phải {@code /payments/vnpay}</h2>
 *
 * <p>Whitelist của dự án khớp theo <b>đường dẫn, không phân biệt HTTP method</b>. Nếu đặt dưới
 * {@code /payments} thì phải mở một nhánh bên trong prefix vốn dĩ yêu cầu đăng nhập — mọi thứ
 * khác lọt vào nhánh đó đều công khai theo. Tách hẳn ra {@code /vnpay} thì ranh giới rõ ràng: chỉ
 * đúng hai endpoint dưới đây là công khai, {@code /payments/**} vẫn phải đăng nhập.</p>
 *
 * <h2>Hai endpoint ở đây KHÔNG trả {@code ApiResponse} — đây là ngoại lệ có chủ đích</h2>
 *
 * <p>Mọi endpoint khác của dự án đều trả {@code ApiResponse<T>}, kèm {@code code/message/result/
 * timestamp/path}. Hai endpoint này không thể:</p>
 * <ul>
 *   <li><b>IPN:</b> VNPay đọc đúng hai khoá {@code RspCode} và {@code Message} ở cấp ngoài cùng.
 *       Bọc trong {@code ApiResponse} là chúng rơi vào {@code result} — VNPay không thấy, coi như
 *       không có phản hồi, và gọi lại 10 lần.</li>
 *   <li><b>Return:</b> trình duyệt khách mở, phải là {@code text/html}.</li>
 * </ul>
 *
 * <p>Đừng "sửa cho đồng bộ" về {@code ApiResponse}. Ngoại lệ này là điều kiện để tích hợp chạy
 * được, không phải chỗ ai đó quên.</p>
 *
 * <h2>Đường dẫn khai tại từng method, không dùng {@code @RequestMapping} ở lớp</h2>
 *
 * <p>Hai lý do, cả hai đều là lỗi thật đã gặp:</p>
 * <ol>
 *   <li><b>Swagger hiển thị đúng đường dẫn.</b> Có {@code @RequestMapping("/vnpay")} ở lớp thì
 *       {@code /v3/api-docs} vẫn ghi {@code /vnpay/ipn} ở <b>cấp operation</b>, {@code /vnpay} ở
 *       cấp path — một số công cụ và một số bản springdoc chỉ đọc {@code path} của operation, nên
 *       người test tay thấy đường dẫn sai.</li>
 *   <li><b>Không có tiền lệ trong dự án.</b> {@code ProductController}, {@code AccountController},
 *       {@code AuthController} đều khai đường dẫn đầy đủ trên từng method
 *       ({@code @GetMapping("/products")}, {@code @PostMapping("/auth/login")}...). Không controller
 *       nào dùng {@code @RequestMapping} mức lớp cho đường dẫn.</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/payments/vnpay")
@RequiredArgsConstructor
public class VnpayCallbackController {

    private final VnpayCallbackService vnpayCallbackService;
    private final VnpayReturnService vnpayReturnService;

    /**
     * Nhận thông báo kết quả thanh toán từ VNPay (server-to-server).
     *
     * <p><b>Đây là nguồn xác nhận thanh toán duy nhất.</b> Return URL chỉ để hiển thị.</p>
     *
     * <p>Nhận {@code Map<String,String>} thay vì DTO bind sẵn: chữ ký được tính trên đúng các cặp
     * (tên, giá trị) VNPay gửi lên. Bind qua DTO rồi dựng lại map là có nguy cơ đổi giá trị (khoảng
     * trắng, dấu cộng, giá trị rỗng) và chữ ký sẽ không bao giờ khớp. Đây cũng là cách
     * {@code AuthController} nhận tham số cho các luồng tương tự.</p>
     *
     * <p><b>Luôn trả HTTP 200</b>, kể cả khi từ chối. VNPay phân biệt thành bại qua {@code RspCode}
     * trong body, không qua status code; trả 4xx/5xx sẽ khiến họ hiểu nhầm là lỗi hạ tầng và retry
     * vô ích.</p>
     *
     * <p><b>Vì sao vẫn tự bắt {@code Exception} ở đây</b> dù controller của dự án không bao giờ làm
     * vậy: {@code GlobalExceptionHandler} có nhánh {@code @ExceptionHandler(Exception.class)} trả
     * {@code ApiResponse} — VNPay không đọc được, và một giao dịch thật biến thành im lặng mất.
     * {@code AuthController} cũng có một chỗ tự bắt, nhưng để <b>dọn cookie rồi ném lại</b> cho
     * handler. Ở đây ném lại là hỏng tích hợp, nên phải nuốt và trả {@code 99}.</p>
     */
    @GetMapping(value = "/ipn", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VnpayIpnAcknowledgement> ipn(HttpServletRequest request) {
        VnpayIpnAcknowledgement acknowledgement;
        try {
            acknowledgement = VnpayIpnAcknowledgement.from(
                    vnpayCallbackService.handleIpn(readParameters(request)));
        } catch (RuntimeException exception) {
            log.error("VNPay IPN crashed unexpectedly", exception);
            acknowledgement = VnpayIpnAcknowledgement.unknownError();
        }

        // Log lại để đối soát: VNPay cũng lưu vết ở phía họ, hai bên phải khớp được.
        log.info("VNPay IPN result: RspCode={} message={}",
                acknowledgement.rspCode(), acknowledgement.message());

        return ResponseEntity.ok(acknowledgement);
    }

    /**
     * Trang kết quả thanh toán cho <b>trình duyệt của khách</b>.
     *
     * <p><b>Endpoint này KHÔNG ghi database.</b> Khách bấm F5, gửi link cho bạn bè, mở lại link
     * hàng tuần sau — mỗi lần như vậy không được là một lần ghi nhận thanh toán. Xác nhận thanh
     * toán là việc của IPN, duy nhất.</p>
     *
     * <p><b>Chữ ký sai vẫn trả HTTP 200</b> kèm trang "Liên kết không hợp lệ". Trả 4xx ở đây là
     * đẩy khách vào trang lỗi trắng của Spring đúng lúc họ vừa trả tiền; một trang giải thích đàng
     * hoàng có ích hơn nhiều. Đổi lại: <b>không đọc database</b> khi chữ ký sai — ta không hiển thị
     * thông tin đơn của bất kỳ ai cho một URL không do VNPay sinh ra.</p>
     *
     * <p>Trả {@code text/html} thay vì {@code ApiResponse} — đây là ngoại lệ thứ hai, lý do ở
     * javadoc lớp.</p>
     */
    @GetMapping(value = "/return", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> returnPage(HttpServletRequest request) {
        VnpayReturnResponse view;
        try {
            view = vnpayReturnService.buildReturnView(readParameters(request));
        } catch (RuntimeException exception) {
            // Không để GlobalExceptionHandler dựng trang lỗi JSON: khách đang ở trình duyệt, họ cần
            // một câu giải thích, không cần một khối JSON.
            log.error("VNPay Return page failed to build", exception);
            view = VnpayReturnResponse.systemError();
        }

        // no-store: trang chứa số tiền và mã đơn của một khách. Đừng để nó nằm trong cache của
        // máy dùng chung, và cũng đừng để F5 hiển thị lại một ảnh chụp cũ.
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(view.toHtml());
    }

    /**
     * Đọc tham số từ query string.
     *
     * <p>Dùng {@code getParameterMap} của container: nó đã URL-decode đúng một lần. Tự decode lại
     * là decode hai lần — dấu {@code +} trong {@code vnp_OrderInfo} sẽ thành khoảng trắng lần nữa
     * và chữ ký hỏng.</p>
     */
    private Map<String, String> readParameters(HttpServletRequest request) {
        Map<String, String> parameters = new HashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (values != null && values.length > 0) {
                parameters.put(name, values[0]);
            }
        });
        return parameters;
    }
}
