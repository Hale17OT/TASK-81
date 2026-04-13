package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.UserPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreferenceEntity, Long> {

    Optional<UserPreferenceEntity> findByUserId(Long userId);
}
