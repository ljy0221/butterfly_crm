package com.b101.butterfly.event.repository;

import com.b101.butterfly.event.entity.EventSchemaField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventSchemaFieldRepository extends JpaRepository<EventSchemaField, Long> {

    List<EventSchemaField> findBySchemaId(Long schemaId);
}
