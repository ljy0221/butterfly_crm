package com.b101.butterfly.auth.repository;

import com.b101.butterfly.auth.entity.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, Long> {

    boolean existsByToken(String token);

    @Modifying
    @Query("DELETE FROM TokenBlacklist tb WHERE tb.expiresAt < :now")
    void deleteAllExpiredBefore(LocalDateTime now);
}
