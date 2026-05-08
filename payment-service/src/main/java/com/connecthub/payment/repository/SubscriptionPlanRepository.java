package com.connecthub.payment.repository;

import com.connecthub.payment.entity.SubscriptionPlan;
import com.connecthub.payment.entity.enums.PlanName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Integer> {

    Optional<SubscriptionPlan> findByPlanName(PlanName planName);

    List<SubscriptionPlan> findByIsActiveTrue();
}
