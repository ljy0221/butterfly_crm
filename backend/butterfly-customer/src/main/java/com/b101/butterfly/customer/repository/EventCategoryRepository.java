package com.b101.butterfly.customer.repository;

import com.b101.butterfly.customer.entity.EventCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventCategoryRepository extends JpaRepository<EventCategory, Long> {

    Optional<EventCategory> findByProjectIdAndName(Long projectId, String name);
}
