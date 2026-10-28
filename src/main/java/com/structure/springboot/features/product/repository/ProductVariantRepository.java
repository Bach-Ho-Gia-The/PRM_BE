package com.structure.springboot.features.product.repository;

import com.structure.springboot.features.product.entity.ProductVariantEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariantEntity, UUID> {

    /**
     * Nạp toàn bộ dòng tồn kho của nhiều sản phẩm trong một câu SQL, <b>khoá bi quan từng dòng</b>.
     *
     * <p>Dùng cho đúng hai chỗ chạm vào số lượng tồn: <b>trừ kho lúc đặt đơn</b> và <b>cộng kho lúc
     * huỷ đơn</b>. Tra từng cặp (sản phẩm, size) trong vòng lặp thì giỏ bao nhiêu món là bấy nhiêu
     * câu SQL; lấy một lượt rồi tra trong bộ nhớ thì số câu SQL không còn phụ thuộc kích thước giỏ.</p>
     *
     * <p><b>Vì sao khoá bi quan chứ không chỉ dựa vào {@code @Version}:</b> {@code @Version} chỉ
     * phát hiện tranh chấp <i>lúc commit</i> rồi đá một giao dịch ra bằng
     * {@code OptimisticLockException} — phát hiện đúng, nhưng muộn: khách đã đi hết luồng đặt hàng
     * mới biết mình thua, và mail xác nhận đã kịp hẹn gửi. Khoá bi quan chặn ngay từ câu đọc, nên
     * người tới sau <b>chờ</b> rồi đọc được số tồn mới nhất thay vì bị đá ra.
     * {@code @Version} vẫn giữ nguyên trên entity làm lớp chặn cuối.</p>
     *
     * <p><b>Vì sao {@code OrderByIdAsc}:</b> khoá được cấp theo cùng một thứ tự ở mọi giao dịch.
     * Hai đơn cùng chứa hai món A và B, nhưng giao dịch này khoá A→B còn giao dịch kia khoá B→A
     * thì hai bên chờ nhau vòng tròn. Sắp thứ tự <b>giảm</b> nguy cơ deadlock — <b>không triệt
     * tiêu</b>: vẫn phải xử lý {@code DeadlockLoserDataAccessException} ở tầng gọi.</p>
     *
     * <p>Vẫn là <b>derived query</b>: {@code @Lock} là annotation, không cần {@code @Query}.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ProductVariantEntity> findByProductIdInOrderByIdAsc(Collection<UUID> productIds);
}
