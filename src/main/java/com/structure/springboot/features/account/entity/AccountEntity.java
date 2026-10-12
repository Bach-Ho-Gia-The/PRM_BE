package com.structure.springboot.features.account.entity;

import com.structure.springboot.features.account.enums.AccountStatus;
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

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleEntity role;
}
