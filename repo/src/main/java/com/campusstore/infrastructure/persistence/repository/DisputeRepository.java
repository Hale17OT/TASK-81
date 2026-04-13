package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.core.domain.model.DisputeStatus;
import com.campusstore.infrastructure.persistence.entity.DisputeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisputeRepository extends JpaRepository<DisputeEntity, Long> {

    List<DisputeEntity> findByUserId(Long userId);

    List<DisputeEntity> findByUserIdAndStatus(Long userId, DisputeStatus status);

    boolean existsByUserIdAndStatus(Long userId, DisputeStatus status);

    /**
     * Anonymize all disputes belonging to a user that is being cryptographically erased:
     * link each row to the deleted-user placeholder and null the user FK. Used by
     * {@code AuditService.runUserDeletion} so the user hard-delete does not get blocked by
     * the dispute FK while the dispute history itself is preserved for forensics.
     * Open disputes are excluded by callers (the service refuses to delete users with
     * any open dispute), so this update only ever rewrites RESOLVED/DISMISSED rows.
     */
    @Modifying
    @Query("UPDATE DisputeEntity d SET d.placeholderId = :placeholderId, d.user = null "
            + "WHERE d.userId = :userId")
    int anonymizeForUser(@Param("userId") Long userId,
                          @Param("placeholderId") Long placeholderId);
}
