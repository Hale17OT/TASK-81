package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.core.domain.model.Role;
import com.campusstore.infrastructure.persistence.entity.UserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, Long> {

    List<UserRoleEntity> findByUserId(Long userId);

    List<UserRoleEntity> findByRole(Role role);

    boolean existsByUserIdAndRole(Long userId, Role role);

    @Modifying
    @Transactional
    void deleteByUserIdAndRole(Long userId, Role role);
}
