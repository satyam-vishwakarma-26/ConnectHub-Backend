package com.connecthub.auth.repository;

import com.connecthub.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByProviderAndProviderId(User.AuthProvider provider, String providerId);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findByStatus(User.UserStatus status);

    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<User> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT u FROM User u WHERE u.username = :username AND u.id != :excludeId")
    Optional<User> findByUsernameExcluding(@Param("username") String username,
                                            @Param("excludeId") Long excludeId);

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.id != :excludeId")
    Optional<User> findByEmailExcluding(@Param("email") String email,
                                         @Param("excludeId") Long excludeId);
}
