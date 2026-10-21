package com.openforge.aimate.repository;

import com.openforge.aimate.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("""
            SELECT u FROM User u
            WHERE (:q IS NULL OR :q = ''
               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(u.displayName, '')) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.pointsBalance = u.pointsBalance - :amount WHERE u.id = :userId AND u.pointsBalance >= :amount")
    int deductPointsIfEnough(@Param("userId") Long userId, @Param("amount") long amount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.pointsBalance = u.pointsBalance + :amount WHERE u.id = :userId")
    int addPoints(@Param("userId") Long userId, @Param("amount") long amount);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByAimatexAccountSub(String aimatexAccountSub);

    /**
     * Login helper: allow username or email.
     */
    Optional<User> findByUsernameOrEmail(String username, String email);
}

