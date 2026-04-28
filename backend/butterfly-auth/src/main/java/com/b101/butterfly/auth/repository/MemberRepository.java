package com.b101.butterfly.auth.repository;

import com.b101.butterfly.auth.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Member> findAllByProjectId(Long projectId);

    // 소프트딜리트된 멤버 포함 조회 (탈퇴 재가입 중복 체크용)
    @Query("SELECT m FROM Member m WHERE m.email = :email")
    Optional<Member> findByEmailIncludingDeleted(String email);
}
