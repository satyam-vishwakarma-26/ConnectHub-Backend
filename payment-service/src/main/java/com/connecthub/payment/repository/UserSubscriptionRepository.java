package com.connecthub.payment.repository;

import com.connecthub.payment.entity.UserSubscription;
import com.connecthub.payment.entity.enums.PlanName;
import com.connecthub.payment.entity.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    Optional<UserSubscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);

    Optional<UserSubscription> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserSubscription> findByStatusAndEndDateBefore(SubscriptionStatus status, LocalDateTime date);

    List<UserSubscription> findByStatusAndEndDateBetween(SubscriptionStatus status,
                                                          LocalDateTime from, LocalDateTime to);

    boolean existsByUserIdAndStatus(Long userId, SubscriptionStatus status);

    @Query("SELECT COUNT(s) FROM UserSubscription s WHERE s.status = :status AND s.planName = :planName")
    long countByStatusAndPlanName(@Param("status") SubscriptionStatus status,
                                   @Param("planName") PlanName planName);
}
