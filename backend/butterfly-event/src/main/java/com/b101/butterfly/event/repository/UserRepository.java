package com.b101.butterfly.event.repository;

import com.b101.butterfly.event.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProjectIdAndExternalId(Long projectId, String externalId);
}
