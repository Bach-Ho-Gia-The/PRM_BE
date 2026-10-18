package com.structure.springboot.features.account.entity;

import com.structure.springboot.features.account.enums.AccountStatus;
import com.structure.springboot.features.account.enums.AuthProvider;
import com.structure.springboot.features.role.entity.RoleEntity;
import com.structure.springboot.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class AccountEntity extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    /**
     * <b>Nullable từ Đợt 6:</b> tài khoản tạo bằng Google không có mật khẩu. Null nghĩa là
     * "chưa đặt mật khẩu" — chủ tài khoản muốn có thì đi qua luồng quên mật khẩu để đặt lần đầu.
     */
    @Column(name = "password")
    private String password;

    /**
     * Tài khoản này sinh ra từ đâu. <b>Không đổi sau khi tạo</b>: liên kết thêm Google vào một
     * tài khoản LOCAL chỉ điền {@code providerId}, không lật cờ này — người dùng vẫn đăng nhập
     * bằng mật khẩu như cũ.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20)
    private AuthProvider provider = AuthProvider.LOCAL;

    /**
     * Claim {@code sub} của Google — khoá định danh thật sự của luồng đăng nhập Google.
     *
     * <p>Google khuyến cáo rõ: KHÔNG dùng email làm khoá vì người dùng đổi được email, còn
     * {@code sub} thì vĩnh viễn không đổi và không bao giờ tái sử dụng.</p>
     */
    @Column(name = "provider_id", unique = true)
    private String providerId;

    /**
     * Để nullable vì {@code ddl-auto: update} KHÔNG thêm được cột NOT NULL vào bảng đã có dữ liệu.
     * Hàng cũ sẽ mang giá trị null và được coi là {@link AccountStatus#ACTIVE}; sau khi chạy một
     * câu UPDATE lấp đầy dữ liệu cũ thì hãy siết lại thành {@code nullable = false}.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    /** Mốc hết khoá tạm. Chỉ có ý nghĩa khi {@code status = LOCKED}; null = khoá vô thời hạn. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    /**
     * Tài khoản này còn được phép đăng nhập / khôi phục mật khẩu không.
     *
     * <p><b>Vì sao không kiểm {@code status == ACTIVE} là xong:</b> khoá tạm TỰ HẾT HẠN theo
     * {@code lockedUntil}, nhưng cột {@code status} thì KHÔNG có ai ghi lại về ACTIVE — nó nằm
     * nguyên ở LOCKED mãi mãi. Chỗ nào chỉ so {@code status} sẽ chặn nhầm người đã hết hạn khoá:
     * họ đăng nhập được (vì {@code AccountPrincipal} có đọc {@code lockedUntil}) nhưng lại không
     * dùng được chức năng quên mật khẩu.</p>
     *
     * <p>{@code status} null là dữ liệu cũ chưa có cột này ⇒ coi như ACTIVE, giống
     * {@code ProductEntity.isVisible()}.</p>
     *
     * <p>Cùng một luật với cặp cờ {@code isEnabled()} + {@code isAccountNonLocked()} của
     * {@code AccountPrincipal}; bên đó buộc phải tách đôi vì đó là giao kèo của {@code UserDetails}.</p>
     */
    public boolean isLoginAllowed() {
        if (status == AccountStatus.DISABLED) {
            return false;
        }
        if (status == AccountStatus.LOCKED) {
            return lockedUntil != null && !lockedUntil.isAfter(Instant.now());
        }
        return true;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleEntity role;
}
