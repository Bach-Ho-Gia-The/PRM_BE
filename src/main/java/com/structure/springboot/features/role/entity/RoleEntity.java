package com.structure.springboot.features.role.entity;

import com.structure.springboot.features.account.entity.AccountEntity;
import com.structure.springboot.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Phía "One" của quan hệ 1 Role - N Account.
 * Phía Account giữ FK qua {@code @ManyToOne @JoinColumn(name = "role_id")}.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class RoleEntity extends BaseEntity {

    @Column(name = "role_name", nullable = false, unique = true, length = 50)
    private String roleName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * CHỈ để đọc ngược. KHÔNG cascade, KHÔNG orphanRemoval: Account không "thuộc sở hữu" của Role,
     * xoá một Role không được phép kéo theo xoá tài khoản. Việc chặn xoá do service kiểm tra.
     */
    @Builder.Default
    @OneToMany(mappedBy = "role")
    private List<AccountEntity> accounts = new ArrayList<>();
}
