package com.structure.springboot.features.payment.service;

import com.structure.springboot.features.payment.dto.response.VnpayReturnResponse;

import java.util.Map;

/**
 * Dựng trang kết quả thanh toán cho <b>trình duyệt của khách</b> sau khi VNPay redirect về.
 *
 * <h2>Khác gì {@link VnpayCallbackService}</h2>
 *
 * <p>Hai đường này nghe giống nhau nhưng trả lời hai câu hỏi khác nhau, và việc trộn chúng là
 * nguồn gốc của phần lớn lỗi tích hợp VNPay:</p>
 *
 * <table border="1">
 *   <caption>So sánh</caption>
 *   <tr><th></th><th>IPN ({@link VnpayCallbackService})</th><th>Return (interface này)</th></tr>
 *   <tr><td>Ai gọi</td><td>server VNPay</td><td>trình duyệt khách</td></tr>
 *   <tr><td>Tin cậy được không</td><td><b>có</b> — nguồn xác nhận duy nhất</td><td><b>không</b></td></tr>
 *   <tr><td>Được ghi database</td><td>có</td><td><b>không, chỉ đọc</b></td></tr>
 *   <tr><td>Trả về</td><td><code>RspCode</code></td><td>HTML</td></tr>
 * </table>
 *
 * <p><b>Return tuyệt đối không được ghi database.</b> Khách bấm F5, gửi link cho bạn bè, hoặc mở
 * lại link cũ hàng tuần sau. Mỗi lần như vậy không được là một lần ghi nhận thanh toán.</p>
 *
 * <p>Cũng vì thế mà trạng thái hiển thị <b>luôn đọc từ database</b>. {@code vnp_ResponseCode} trên
 * URL chỉ là lời khai của trình duyệt, không phải bằng chứng.</p>
 */
public interface VnpayReturnService {

    /**
     * Dựng view cho trang kết quả.
     *
     * <p>Không bao giờ ném: trang này là thứ khách nhìn thấy ngay sau khi trả tiền, ném exception ở
     * đây nghĩa là khách nhận một trang lỗi trắng của Spring đúng vào lúc họ đang lo lắng nhất.
     * Lỗi bất ngờ ⇒ trả {@link com.structure.springboot.features.payment.enums.VnpayReturnState#SYSTEM_ERROR}, và controller ghi log.</p>
     *
     * @param parameters toàn bộ tham số VNPay gửi kèm trên query string
     * @return view đã sẵn sàng render
     */
    VnpayReturnResponse buildReturnView(Map<String, String> parameters);
}
