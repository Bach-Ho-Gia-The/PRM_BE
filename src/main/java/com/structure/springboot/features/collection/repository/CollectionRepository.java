package com.structure.springboot.features.collection.repository;

import com.structure.springboot.features.collection.entity.CollectionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectionRepository extends JpaRepository<CollectionEntity, UUID>,
        JpaSpecificationExecutor<CollectionEntity> {

    /** Các thao tác ghi cùng BST phải tuần tự; so expectedVersion SAU khi đã có khoá. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CollectionEntity> findWithLockById(UUID id);

    /** Kể cả BST đã xoá mềm; service phải lọc trạng thái trước khi trả dữ liệu công khai. */
    Optional<CollectionEntity> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);
}
