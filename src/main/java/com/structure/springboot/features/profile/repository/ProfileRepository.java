package com.structure.springboot.features.profile.repository;

import com.structure.springboot.features.profile.entity.ProfileEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<ProfileEntity, UUID> {

    /**
     * Tra hồ sơ theo chủ sở hữu.
     *
     * <p>{@code @EntityGraph("account")} là bắt buộc: {@code ProfileResponse} có {@code email}
     * lấy từ account, không nạp sẵn thì mapper kích hoạt thêm một truy vấn nữa.</p>
     */
    @EntityGraph(attributePaths = {"account"})
    Optional<ProfileEntity> findByAccountId(UUID accountId);

    /** Nạp hồ sơ của nhiều tài khoản một lượt — dùng cho màn danh sách hội thoại của admin. */
    List<ProfileEntity> findByAccountIdIn(Collection<UUID> accountIds);

    /** Serialize avatar uploads and legacy PATCH updates for this profile. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProfileEntity> findWithLockByAccountId(UUID accountId);

    /** Dọn hồ sơ khi tài khoản bị xoá — không có bước này thì khoá ngoại chặn lệnh xoá. */
    void deleteByAccountId(UUID accountId);
}
