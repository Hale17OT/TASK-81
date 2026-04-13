package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.DataRetentionPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DataRetentionPolicyRepository extends JpaRepository<DataRetentionPolicyEntity, Long> {

    Optional<DataRetentionPolicyEntity> findByEntityType(String entityType);
}
