package com.b101.butterfly.event.repository;

import com.b101.butterfly.event.entity.EventCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventCategoryRepository extends JpaRepository<EventCategory, Long> {

    Optional<EventCategory> findByProjectIdAndName(Long projectId, String name);

    Optional<EventCategory> findByIdAndProjectId(Long id, Long projectId);

    List<EventCategory> findAllByProjectIdOrderByNameAsc(Long projectId);

    boolean existsByProjectIdAndName(Long projectId, String name);
}
