package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.CrawlerJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrawlerJobRepository extends JpaRepository<CrawlerJobEntity, Long> {

    List<CrawlerJobEntity> findByIsActiveTrue();
}
