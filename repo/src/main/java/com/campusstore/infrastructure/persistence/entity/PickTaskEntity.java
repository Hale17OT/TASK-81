package com.campusstore.infrastructure.persistence.entity;

import com.campusstore.core.domain.model.PickStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pick_task")
public class PickTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", insertable = false, updatable = false)
    private Long requestId;

    @Column(name = "location_id", insertable = false, updatable = false)
    private Long locationId;

    @Column(name = "assigned_to", insertable = false, updatable = false)
    private Long assignedTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PickStatus status;

    @Column(name = "pick_path_cost")
    private BigDecimal pickPathCost;

    @Column(name = "sequence_order")
    private Integer sequenceOrder;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private ItemRequestEntity request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private StorageLocationEntity location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private UserEntity assignedUser;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(Long assignedTo) {
        this.assignedTo = assignedTo;
    }

    public PickStatus getStatus() {
        return status;
    }

    public void setStatus(PickStatus status) {
        this.status = status;
    }

    public BigDecimal getPickPathCost() {
        return pickPathCost;
    }

    public void setPickPathCost(BigDecimal pickPathCost) {
        this.pickPathCost = pickPathCost;
    }

    public Integer getSequenceOrder() {
        return sequenceOrder;
    }

    public void setSequenceOrder(Integer sequenceOrder) {
        this.sequenceOrder = sequenceOrder;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public ItemRequestEntity getRequest() {
        return request;
    }

    public void setRequest(ItemRequestEntity request) {
        this.request = request;
    }

    public StorageLocationEntity getLocation() {
        return location;
    }

    public void setLocation(StorageLocationEntity location) {
        this.location = location;
    }

    public UserEntity getAssignedUser() {
        return assignedUser;
    }

    public void setAssignedUser(UserEntity assignedUser) {
        this.assignedUser = assignedUser;
    }
}
