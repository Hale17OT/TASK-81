package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.core.domain.model.RequestStatus;
import com.campusstore.infrastructure.persistence.entity.ItemRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ItemRequestRepository extends JpaRepository<ItemRequestEntity, Long> {

    Page<ItemRequestEntity> findByRequesterId(Long requesterId, Pageable pageable);

    Page<ItemRequestEntity> findByApproverId(Long approverId, Pageable pageable);

    Page<ItemRequestEntity> findByApproverIdAndStatus(Long approverId, RequestStatus status, Pageable pageable);

    Page<ItemRequestEntity> findByStatus(RequestStatus status, Pageable pageable);

    @Query("SELECT ir FROM ItemRequestEntity ir JOIN ir.item i WHERE i.departmentId = :departmentId AND ir.status = 'PENDING_APPROVAL'")
    Page<ItemRequestEntity> findPendingRequestsByDepartment(@Param("departmentId") Long departmentId, Pageable pageable);

    @Query("SELECT ir FROM ItemRequestEntity ir JOIN ir.item i WHERE i.departmentId = :departmentId AND ir.status = 'PENDING_APPROVAL' AND ir.requesterId != :excludeRequesterId")
    Page<ItemRequestEntity> findPendingRequestsByDepartmentExcludingRequester(@Param("departmentId") Long departmentId, @Param("excludeRequesterId") Long excludeRequesterId, Pageable pageable);

    List<ItemRequestEntity> findByStatusAndPickupDeadlineBefore(RequestStatus status, Instant deadline);

    List<ItemRequestEntity> findByRequesterIdAndItemIdAndStatusIn(Long requesterId, Long itemId, List<RequestStatus> statuses);

    List<ItemRequestEntity> findByStatusAndUpdatedAtBefore(RequestStatus status, Instant cutoff);
}
