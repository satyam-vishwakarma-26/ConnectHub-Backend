package com.connecthub.payment.config;

import com.connecthub.payment.entity.SubscriptionPlan;
import com.connecthub.payment.entity.enums.PlanName;
import com.connecthub.payment.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final SubscriptionPlanRepository planRepository;

    @Override
    public void run(String... args) {
        if (planRepository.count() == 0) {

            // ── FREE Plan ──────────────────────────────────
            SubscriptionPlan freePlan = SubscriptionPlan.builder()
                    .planName(PlanName.FREE)
                    .price(0.00)
                    .currency("INR")
                    .billingCycle("MONTHLY")
                    .maxRooms(5)
                    .maxMembersPerRoom(50)
                    .maxFileSizeMb(5)
                    .messageHistoryDays(30)
                    .maxDevices(1)
                    .customRoomAvatar(false)
                    .priorityNotifications(false)
                    .readReceipts(false)
                    .messageReactions(false)
                    .isActive(true)
                    .build();

            planRepository.save(freePlan);

            // ── PRO Plan ───────────────────────────────────
            SubscriptionPlan proPlan = SubscriptionPlan.builder()
                    .planName(PlanName.PRO)
                    .price(199.00)
                    .currency("INR")
                    .billingCycle("MONTHLY")
                    .maxRooms(-1)                // unlimited
                    .maxMembersPerRoom(-1)        // unlimited
                    .maxFileSizeMb(100)
                    .messageHistoryDays(-1)       // unlimited
                    .maxDevices(-1)              // unlimited
                    .customRoomAvatar(true)
                    .priorityNotifications(true)
                    .readReceipts(true)
                    .messageReactions(true)
                    .isActive(true)
                    .build();

            planRepository.save(proPlan);

            log.info("✅ Plans initialized: FREE (₹0) and PRO (₹199/month)");
        } else {
            log.info("Plans already exist. Skipping initialization.");
        }
    }
}
