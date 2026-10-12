package com.structure.springboot.features.account.repository;

import com.structure.springboot.features.account.entity.AccountEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCaseAndIdNot(String username, UUID id);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    // Chặn xoá Role khi vẫn còn tài khoản trỏ tới -> thay cho cascade đã gỡ bỏ
    boolean existsByRoleId(UUID roleId);

    @EntityGraph(attributePaths = {"role"})
    Optional<AccountEntity> findByEmailIgnoreCase(String email);

    @Override
    @EntityGraph(attributePaths = {"role"})
    Optional<AccountEntity> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"role"})
    List<AccountEntity> findAll();

    // Bản phân trang là một method KHÁC, không kế thừa @EntityGraph của bản List ở trên.
    // Thiếu dòng override này thì mỗi trang 20 tài khoản sẽ bắn thêm 20 câu SELECT role.
    @Override
    @EntityGraph(attributePaths = {"role"})
    Page<AccountEntity> findAll(Pageable pageable);
}
