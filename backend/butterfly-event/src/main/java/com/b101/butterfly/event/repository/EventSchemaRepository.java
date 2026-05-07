package com.b101.butterfly.event.repository;

import com.b101.butterfly.event.entity.EventSchema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EventSchemaRepository extends JpaRepository<EventSchema, Long> {

    Optional<EventSchema> findByCategoryIdAndIsCurrentTrue(Long categoryId);

    List<EventSchema> findByProjectIdAndCategoryIdOrderByVersionDesc(Long projectId, Long categoryId);

    boolean existsByCategoryId(Long categoryId);

    @Modifying
    @Query("UPDATE EventSchema s SET s.isCurrent = false WHERE s.categoryId = :categoryId AND s.isCurrent = true")
    void deactivateCurrentByCategoryId(Long categoryId);
}
