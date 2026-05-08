package com.connecthub.notification.repository;

import com.connecthub.notification.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    List<FcmToken> findByUserId(Long userId);

    Optional<FcmToken> findByUserIdAndToken(Long userId, String token);

    boolean existsByUserIdAndToken(Long userId, String token);

    void deleteByUserIdAndToken(Long userId, String token);

    void deleteByUserId(Long userId);
}
