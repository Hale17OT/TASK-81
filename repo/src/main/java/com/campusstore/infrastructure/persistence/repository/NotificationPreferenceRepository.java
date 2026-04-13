package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.core.domain.model.NotificationType;
import com.campusstore.infrastructure.persistence.entity.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreferenceEntity, Long> {

    List<NotificationPreferenceEntity> findByUserId(Long userId);

    Optional<NotificationPreferenceEntity> findByUserIdAndNotificationType(Long userId, NotificationType notificationType);
}
