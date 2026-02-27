package com.openforge.aimate.repository;

import com.openforge.aimate.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    /**
     * Login helper: allow username or email.
     */
    Optional<User> findByUsernameOrEmail(String username, String email);
}

