package com.b101.butterfly.auth.repository;

import com.b101.butterfly.auth.constant.MemberStatus;
import com.b101.butterfly.auth.entity.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsById(Long id);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Member> findAllByProjectId(Long projectId);

    // 네이티브 쿼리로 @SQLRestriction 우회 — 탈퇴 계정 포함 이메일 중복 체크용
    @Query(value = "SELECT COUNT(*) FROM members WHERE email = :email", nativeQuery = true)
    int countByEmailIncludingDeleted(@Param("email") String email);

    @Query("SELECT m.name FROM Member m WHERE m.id = :id")
    String findNameById(@Param("id") Long id);

    List<Member> findAllByProjectIdAndStatus(Long projectId, MemberStatus status);
}
