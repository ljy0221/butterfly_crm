package com.b101.butterfly.event.repository;

import com.b101.butterfly.event.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProjectIdAndExternalId(Long projectId, String externalId);

    @Query("""
            SELECT u FROM User u
            WHERE u.projectId = :projectId
              AND u.externalId <> '__anonymous__'
              AND (
                    :search IS NULL
                    OR :search = ''
                    OR LOWER(u.externalId) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(u.phone, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<User> searchCustomers(
            @Param("projectId") Long projectId,
            @Param("search") String search,
            Pageable pageable
    );
}
