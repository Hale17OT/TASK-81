package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.DeletedUserPlaceholderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeletedUserPlaceholderRepository extends JpaRepository<DeletedUserPlaceholderEntity, Long> {

    Optional<DeletedUserPlaceholderEntity> findByOriginalUserId(Long originalUserId);
}
