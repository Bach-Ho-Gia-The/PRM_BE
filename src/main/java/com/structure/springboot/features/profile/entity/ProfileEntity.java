package com.structure.springboot.features.profile.entity;

import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.features.profile.enums.Gender;
import com.structure.springboot.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * Hồ sơ cá nhân — tách hẳn khỏi {@code accounts}.
 *
 * <p><b>Phân vai rõ ràng:</b> {@code accounts} giữ phần danh tính đăng nhập (email,
 * mật khẩu, quyền, trạng thái khoá) — đụng vào là đụng bảo mật. {@code profiles} giữ phần con
 * người (tên thật, số điện thoại, ngày sinh, ảnh đại diện) — sửa thoải mái, không ảnh hưởng
 * phiên đăng nhập.</p>
 *
 * <p><b>Không có cột email:</b> email đã là duy nhất trên {@code accounts} và được dùng làm
 * {@code sub} của token lẫn khoá cắm mốc đăng xuất. Nhân bản xuống đây là tạo ra hai nguồn sự
 * thật, sau này không ai biết gửi mail vào cái nào. {@code ProfileResponse} vẫn trả ra email,
 * nhưng đọc từ account.</p>
 *
 * <p><b>Mọi cột đều cho phép null:</b> hồ sơ được tạo rỗng ngay lúc đăng ký, người dùng điền dần.
 * Bắt {@code NOT NULL} ở đây là chặn luôn bước đăng ký.</p>
 */
@Entity
@Table(
        name = "profiles",
        // Postgres không tự index khoá ngoại, mà findByAccountId là truy vấn chạy nhiều nhất của bảng này.
        // UNIQUE vừa tạo index vừa ép đúng quan hệ 1-1: một tài khoản không thể có hai hồ sơ.
        uniqueConstraints = @UniqueConstraint(name = "uk_profiles_account_id", columnNames = "account_id")
)
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class ProfileEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "address", length = 500)
    private String address;
}
