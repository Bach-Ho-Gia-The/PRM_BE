package com.structure.springboot.features.auth.security;

import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.account.enums.AccountStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Danh tính người dùng ở thời điểm đăng nhập (adapter giữa {@link AccountEntity} và Spring Security).
 *
 * <p>Giữ sẵn {@code id} và {@code roleName} để sau khi xác thực xong là build JWT được ngay,
 * KHÔNG phải query DB lần thứ hai.</p>
 *
 * <p>{@code getUsername()} trả về <b>email</b> — đúng hợp đồng của {@code UserDetails} (chuỗi
 * dùng để xác thực). Cột {@code accounts.username} đã bị xoá: danh tính đăng nhập chỉ còn email,
 * tên hiển thị thuộc về {@code profiles.fullName}.</p>
 */
@Getter
@RequiredArgsConstructor
public class AccountPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String password;
    private final String roleName;
    private final AccountStatus status;
    private final Instant lockedUntil;

    public static AccountPrincipal from(AccountEntity account) {
        return new AccountPrincipal(
                account.getId(),
                account.getEmail(),
                account.getPassword(),
                account.getRole() != null ? account.getRole().getRoleName() : null,
                account.getStatus(),
                account.getLockedUntil()
        );
    }

    /** Chuỗi định danh mà Spring Security dùng để xác thực — dự án đăng nhập bằng email. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roleName == null
                ? List.of()
                // Tiền tố "ROLE_" là quy ước của Spring: hasRole('ADMIN') đi tìm authority ROLE_ADMIN.
                : List.of(new SimpleGrantedAuthority("ROLE_" + roleName));
    }

    /**
     * Tài khoản bị vô hiệu hoá thì không đăng nhập được. Dữ liệu cũ chưa có cột {@code status}
     * (null) được coi là ACTIVE, nếu không cả hệ thống sẽ bị khoá ngay khi triển khai bản này.
     */
    @Override
    public boolean isEnabled() {
        return status != AccountStatus.DISABLED;
    }

    /**
     * {@code LOCKED} + {@code lockedUntil} còn ở tương lai ⇒ đang khoá tạm.
     * {@code lockedUntil} đã qua ⇒ tự mở, khỏi cần job dọn dẹp.
     * {@code lockedUntil} null ⇒ khoá vô thời hạn, chờ admin mở tay.
     */
    @Override
    public boolean isAccountNonLocked() {
        if (status != AccountStatus.LOCKED) {
            return true;
        }
        return lockedUntil != null && !lockedUntil.isAfter(Instant.now());
    }

    // isAccountNonExpired / isCredentialsNonExpired vẫn dùng default = true của UserDetails
    // (Spring Security 7) vì dự án chưa có khái niệm hạn tài khoản hay hạn mật khẩu.
}
