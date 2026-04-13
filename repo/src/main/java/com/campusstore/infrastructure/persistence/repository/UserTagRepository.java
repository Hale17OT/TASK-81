package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.UserTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface UserTagRepository extends JpaRepository<UserTagEntity, Long> {

    List<UserTagEntity> findByUserId(Long userId);

    @Modifying
    @Transactional
    void deleteByUserIdAndTag(Long userId, String tag);
}
