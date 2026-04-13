package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.UserContactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserContactRepository extends JpaRepository<UserContactEntity, Long> {

    List<UserContactEntity> findByUserId(Long userId);
}
