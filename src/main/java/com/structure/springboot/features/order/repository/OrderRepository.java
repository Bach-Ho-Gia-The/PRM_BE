package com.structure.springboot.features.order.repository;

import com.structure.springboot.features.order.entity.OrderEntity;
import jakarta.persistence.LockModeType;
import com.structure.springboot.features.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Truy cập bảng {@code orders}.
 *
 * <h2>Quy ước đặt tên cho bản CÓ KHOÁ</h2>
 *
 * <p>Spring Data chỉ bỏ qua phần chữ nằm <b>giữa {@code find} và {@code By}</b>; mọi chữ nằm
 * <b>sau {@code By}</b> đều bị hiểu là tên thuộc tính của entity. Vì vậy:</p>
 * <ul>
 *   <li>{@code findWithItemsById} — chạy được: {@code WithItems} đứng trước {@code By} nên bị bỏ qua.</li>
 *   <li>{@code findForUpdateById} — chạy được: {@code ForUpdate} đứng trước {@code By}.</li>
 *   <li>{@code findByIdForUpdate} — <b>HỎNG</b>: {@code id.forUpdate} bị coi là đường dẫn thuộc tính
 *       ⇒ lúc khởi động ném {@code PropertyReferenceException: No property 'forUpdate' found} ⇒
 *       <b>toàn bộ ứng dụng không start</b>, không phải chỉ riêng endpoint.</li>
 * </ul>
 * <p>Nên từ phân biệt <b>bắt buộc</b> phải nằm trước {@code By}. Đây cũng là lý do hai method có
 * khoá bên dưới mang tên khác hẳn bản không khoá, thay vì thêm hậu tố.</p>
 */
@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, UUID>,
        JpaSpecificationExecutor<OrderEntity> {

    /**
     * Đơn kèm dòng hàng, dành cho màn chi tiết của admin và cho việc hoàn kho khi huỷ.
     *
     * <p>Không có {@code @EntityGraph} thì mỗi lần đụng {@code order.getItems()} lại thêm một câu
     * SQL, mà hoàn kho thì bắt buộc phải duyệt hết dòng hàng.</p>
     */
    @EntityGraph(attributePaths = {"items"})
    Optional<OrderEntity> findWithItemsById(UUID id);

    /** Bản tra theo mã đơn cho màn quản trị; cùng @EntityGraph nên cũng không N+1. */
    @EntityGraph(attributePaths = {"items"})
    Optional<OrderEntity> findWithItemsByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    /**
     * Tra đơn theo mã NHƯNG buộc phải khớp chủ sở hữu.
     *
     * <p>Lọc ngay trong câu SQL thay vì tải đơn lên rồi mới so {@code accountId} trong Java:
     * quên một dòng so sánh ở service là lộ đơn hàng người khác. Ở đây muốn quên cũng không được.</p>
     */
    @EntityGraph(attributePaths = {"items"})
    Optional<OrderEntity> findByOrderNumberAndAccountId(String orderNumber, UUID accountId);

    /**
     * Đơn kèm dòng hàng, <b>khoá bi quan trên chính dòng đơn</b> — dùng khi sắp đổi trạng thái.
     *
     * <p><b>Đây là chốt chặn thứ nhất chống hoàn kho hai lần.</b> Khách huỷ, shop huỷ, tác vụ quét
     * hết hạn và IPN có thể cùng nhắm một đơn. Đường tới trước giữ khoá và ghi {@code CANCELLED};
     * đường tới sau <b>chờ</b> cho giao dịch đầu kết thúc, đọc lại thấy trạng thái đã đổi nên bị
     * chặn — thay vì cả hai cùng đọc ra {@code PENDING} rồi cùng cộng kho.</p>
     *
     * <p>Khoá trên <b>đơn</b> chứ không phải trên tồn kho là có chủ đích: câu hỏi ở đây là "đơn này
     * đã được xử lý chưa", không phải "dòng kho này có bị ai sửa không". Việc khoá tồn kho thuộc
     * {@code ProductVariantRepository.findByProductIdInOrderByIdAsc}.</p>
     *
     * <p>Khoá <b>tự nhả</b> khi giao dịch kết thúc (commit hoặc rollback) — không có thao tác
     * "trả khoá" nào trong code.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"items"})
    Optional<OrderEntity> findForUpdateWithItemsById(UUID id);

    /**
     * Bản khoá theo <b>mã đơn của chính chủ sở hữu</b> — dùng cho luồng khách tự huỷ và luồng mở
     * link thanh toán.
     *
     * <p>Lọc luôn {@code accountId} ngay trong câu SQL: quên một dòng so sánh ở service là huỷ được
     * đơn của người khác. Ở đây muốn quên cũng không được.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"items"})
    Optional<OrderEntity> findForUpdateByOrderNumberAndAccountId(String orderNumber, UUID accountId);

    /**
     * Bản khoá theo <b>mã đơn, không lọc chủ sở hữu</b> — dùng cho thao tác của SHOP trên đơn của
     * khách, cụ thể là xác nhận đã thu tiền mặt đơn COD.
     *
     * <p>Không có {@code accountId} vì đây không phải tài nguyên của người đang đăng nhập: shop
     * thao tác trên đơn của khách. Phân quyền do {@code @PreAuthorize("hasRole('ADMIN')")} ở
     * controller lo, không phải lọc theo chủ sở hữu ở câu SQL.</p>
     *
     * <p><b>Vì sao vẫn khoá:</b> hai nhân viên cùng bấm "đã thu tiền", hoặc một bên xác nhận thu
     * tiền trong khi bên kia đang huỷ đơn — khoá dòng đơn làm hai luồng xếp hàng, ai vào sau đọc
     * được trạng thái đã đổi. Cùng cơ chế {@link #findForUpdateWithItemsById} dùng cho IPN.</p>
     *
     * <p><b>Không {@code @EntityGraph}:</b> xác nhận thu tiền COD không chạm dòng hàng (không hoàn
     * kho, không đổi tồn), nên nạp kèm {@code items} là nạp thừa.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrderEntity> findForUpdateByOrderNumber(String orderNumber);

    /** Danh sách đơn của tôi. KHÔNG fetch items — màn hình danh sách không dùng tới. */
    /**
     * Đơn quá hạn thanh toán, dùng cho tác vụ quét hết hạn.
     *
     * <p><b>Ba điều kiện, và cả ba đều cần:</b></p>
     * <ol>
     *   <li>{@code status = PENDING} — đơn đã xác nhận, đang giao hay đã huỷ thì không còn liên quan
     *       tới hạn thanh toán.</li>
     *   <li>{@code paymentDueAt <= now} — đã quá hạn. Đơn COD có {@code paymentDueAt = null} nên
     *       <b>tự động bị loại</b>: phép so sánh với {@code null} không bao giờ đúng. Đó là hành vi
     *       đúng chứ không phải sơ suất — COD không có hạn.</li>
     *   <li>{@code paymentDueAt >= earliestDueAt} — <b>cận dưới</b>. Đơn quá hạn từ nhiều ngày trước
     *       thì đã được xử lý ở những lượt quét rất cũ; quét lại chúng mỗi phút là gọi VNPay vô ích.
     *       Cận dưới cũng chặn trường hợp xấu nhất sau khi khôi phục dữ liệu cũ.</li>
     * </ol>
     *
     * <p><b>Vì sao là derived query, không {@code @Query}:</b> cả ba điều kiện đều là so sánh trên
     * cột của chính entity, không cần join, không cần {@code coalesce} hay biểu thức SQL nào. Viết
     * derived thì Spring Data sinh câu SQL và kiểm luôn tên thuộc tính lúc khởi động.</p>
     *
     * <p><b>Không {@code @EntityGraph} ở đây, có chủ ý:</b> lượt quét chỉ cần mã đơn để quyết định;
     * dòng hàng chỉ cần khi thực sự huỷ đơn, và chặng chốt nạp lại đơn <b>kèm dòng hàng và kèm
     * khoá</b> bằng {@link #findForUpdateWithItemsById}. Nạp kèm ở bước quét là nạp hàng nghìn dòng
     * cho những đơn có thể không bao giờ bị huỷ.</p>
     *
     * @param status        trạng thái cần lọc, luôn là {@code PENDING}
     * @param now           mốc "bây giờ" do người gọi truyền, không dùng {@code CURRENT_TIMESTAMP} —
     *                      xem javadoc {@code OrderService.expireOverdueOrders}
     * @param earliestDueAt cận dưới của hạn thanh toán
     * @param pageable      giới hạn số đơn mỗi lượt và thứ tự ưu tiên
     */
    List<OrderEntity> findByStatusAndPaymentDueAtLessThanEqualAndPaymentDueAtGreaterThanEqual(
            OrderStatus status, Instant now, Instant earliestDueAt, Pageable pageable);

    Page<OrderEntity> findByAccountId(UUID accountId, Pageable pageable);

    /**
     * Tài khoản này đã từng đặt đơn nào chưa.
     *
     * <p>Dùng {@code exists} chứ không đếm hay tải danh sách: chỉ cần biết có hay không, để
     * phía tài khoản chặn lệnh xoá trước khi database kịp ném lỗi khoá ngoại.</p>
     */
    boolean existsByAccountId(UUID accountId);
}
