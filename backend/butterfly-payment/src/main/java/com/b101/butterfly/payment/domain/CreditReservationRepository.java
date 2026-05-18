package com.b101.butterfly.payment.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditReservationRepository extends JpaRepository<CreditReservation, Long> {

    Optional<CreditReservation> findByCampaignExecutionId(Long campaignExecutionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM CreditReservation r
            WHERE r.campaignExecutionId = :campaignExecutionId
            """)
    Optional<CreditReservation> findByCampaignExecutionIdForUpdate(
            @Param("campaignExecutionId") Long campaignExecutionId
    );
}
