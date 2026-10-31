package com.structure.springboot.features.payment.service;

import com.structure.springboot.features.payment.dto.response.VnpayIpnResponse;

import java.util.Map;

/**
 * Xử lý thông báo kết quả thanh toán từ VNPay.
 *
 * <p><b>Đây là nguồn xác nhận thanh toán DUY NHẤT của hệ thống.</b> Return URL chỉ để hiển thị.
 * Nếu ai đó sau này thêm đường "xác nhận thanh toán" khác, hãy đọc lại luật này trước: hai nguồn
 * cùng được phép ghi {@code PAID} là hai nguồn có thể mâu thuẫn, và không có cách nào biết cái nào
 * đúng.</p>
 */
public interface VnpayCallbackService {

    /**
     * Xử lý một lần gọi IPN.
     *
     * <p>Nhận {@code Map} tham số <b>chưa qua xử lý</b> chứ không nhận DTO bind sẵn: chữ ký VNPay
     * được tính trên đúng các cặp (tên, giá trị) mà họ gửi, nên phải giữ nguyên bản gốc. Bind vào
     * DTO trước rồi mới dựng lại map là có nguy cơ đổi giá trị (khoảng trắng, dấu cộng, thứ tự) và
     * chữ ký sẽ không bao giờ khớp.</p>
     *
     * <p><b>Không bao giờ ném ngoại lệ.</b> Mọi lỗi đều quy về một {@code RspCode}. Endpoint IPN là
     * chỗ công khai, ai cũng gọi được; ném lỗi ra ngoài là mở đường làm sập luồng xử lý bằng một
     * request rác, và VNPay thì sẽ hiểu nhầm là "lỗi tạm thời" rồi gọi lại 10 lần.</p>
     */
    VnpayIpnResponse handleIpn(Map<String, String> parameters);
}
