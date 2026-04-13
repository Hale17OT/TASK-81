package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.core.domain.model.PickStatus;
import com.campusstore.infrastructure.persistence.entity.PickTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PickTaskRepository extends JpaRepository<PickTaskEntity, Long> {

    List<PickTaskEntity> findByRequestId(Long requestId);

    Page<PickTaskEntity> findByAssignedTo(Long assignedTo, Pageable pageable);

    Page<PickTaskEntity> findByStatus(PickStatus status, Pageable pageable);

    @Query("SELECT pt.locationId, COUNT(pt) FROM PickTaskEntity pt WHERE pt.status = 'COMPLETED' AND pt.completedAt >= :since GROUP BY pt.locationId")
    List<Object[]> countCompletedPicksByLocationSince(@Param("since") Instant since);

    List<PickTaskEntity> findByStatusIn(List<PickStatus> statuses);
}
