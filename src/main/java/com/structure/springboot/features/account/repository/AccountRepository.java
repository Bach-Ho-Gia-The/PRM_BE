package com.structure.springboot.features.account.repository;

import com.structure.springboot.features.account.entity.AccountEntity;
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

    @EntityGraph(attributePaths = {"role"})
    Optional<AccountEntity> findByUsernameIgnoreCase(String username);

    @Override
    @EntityGraph(attributePaths = {"role"})
    Optional<AccountEntity> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"role"})
    List<AccountEntity> findAll();
}
