package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.core.domain.model.AccountStatus;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    Page<UserEntity> findByAccountStatus(AccountStatus status, Pageable pageable);

    @Query("SELECT u FROM UserEntity u WHERE u.departmentId = :deptId")
    List<UserEntity> findByDepartmentId(@Param("deptId") Long departmentId);

    @Query("SELECT u FROM UserEntity u JOIN u.roles r WHERE r.role = 'TEACHER' AND u.departmentId = :deptId AND u.id != :excludeUserId")
    List<UserEntity> findPeerTeachersInDepartment(@Param("deptId") Long departmentId, @Param("excludeUserId") Long excludeUserId);

    @Query("SELECT u FROM UserEntity u WHERE u.accountStatus IN ('DISABLED', 'BLACKLISTED') AND u.disabledAt <= :cutoff")
    List<UserEntity> findUsersEligibleForDeletion(@Param("cutoff") Instant cutoff);
}
