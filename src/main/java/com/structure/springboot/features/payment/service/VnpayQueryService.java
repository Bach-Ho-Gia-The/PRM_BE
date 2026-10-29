package com.structure.springboot.features.payment.service;

import com.structure.springboot.features.payment.dto.response.VnpayQueryResult;
import com.structure.springboot.features.payment.entity.PaymentEntity;

/**
 * Hỏi VNPay kết quả thật của một lần thử thanh toán.
 *
 * <h2>Vì sao có service này</h2>
 *
 * <p>Trạng thái {@code PENDING} trong bảng {@code payments} <b>không</b> có nghĩa là khách chưa trả
 * tiền. Nó chỉ có nghĩa là <b>IPN chưa về</b>. Hai chuyện đó khác nhau, và không có cách nào phân
 * biệt bằng dữ liệu trong database của mình — phải hỏi bên đã trừ tiền.</p>
 *
 * <p>Hỏi VNPay là việc của lớp này. <b>Quyết định làm gì với câu trả lời thì không phải</b> — service
 * này chỉ trả lời trung thực "VNPay nói gì", người gọi (tác vụ quét đơn hết hạn) mới là chỗ quyết
 * định huỷ hay không.</p>
 *
 * <h2>Hợp đồng quan trọng nhất</h2>
 *
 * <p><b>Hàm này không bao giờ ném ngoại lệ vì lý do kỹ thuật.</b> Không gọi được VNPay, timeout, body
 * hỏng, chữ ký sai, lệch số tiền — tất cả trả về verdict
 * {@link com.structure.springboot.features.payment.enums.VnpayQueryVerdict#UNKNOWN}, không ném.</p>
 *
 * <p>Lý do: người gọi sẽ chạy trong vòng lặp trên nhiều đơn. Một ngoại lệ lọt ra ngoài sẽ làm dừng cả
 * lượt quét, và tệ hơn, nếu người gọi bắt ngoại lệ rồi hiểu nhầm đó là "VNPay từ chối" thì đã thành
 * một đường huỷ đơn không an toàn. Đẩy mọi bất định vào {@code UNKNOWN} là cách duy nhất để <b>không
 * có nhánh nào lỡ tay huỷ</b>.</p>
 */
public interface VnpayQueryService {

    /**
     * Hỏi VNPay về lần thử thanh toán đang có.
     *
     * <p>Không truyền {@code txnRef} và {@code amount} rời rạc mà truyền cả {@link PaymentEntity}:
     * hai giá trị kia <b>phải</b> lấy từ chính bản ghi sẽ được đối chiếu. Nhận rời rạc thì người gọi
     * có thể truyền {@code txnRef} của bản ghi này với {@code amount} của bản ghi khác — lỗi biên
     * dịch không chặn được, còn truyền cả entity thì không có đường nào ghép sai.</p>
     *
     * <p>{@code null} hoặc bản ghi chưa được lưu (không có {@code createdAt}) ⇒ {@code UNKNOWN}: không
     * có gì để hỏi thì không được kết luận gì.</p>
     *
     * @param payment bản ghi thanh toán cần hỏi, có thể {@code null}
     * @return luôn khác {@code null}; {@code verdict} khác {@code null}
     */
    VnpayQueryResult query(PaymentEntity payment);
}
