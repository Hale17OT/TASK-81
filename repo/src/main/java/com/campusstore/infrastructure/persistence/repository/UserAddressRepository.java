package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.UserAddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddressEntity, Long> {

    List<UserAddressEntity> findByUserId(Long userId);
}
