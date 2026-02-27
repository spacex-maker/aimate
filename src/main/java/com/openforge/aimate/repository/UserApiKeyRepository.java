package com.openforge.aimate.repository;

import com.openforge.aimate.domain.UserApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserApiKeyRepository extends JpaRepository<UserApiKey, Long> {

    List<UserApiKey> findByUserIdAndIsActiveTrue(Long userId);

    List<UserApiKey> findByUserIdAndProviderAndIsActiveTrue(Long userId, String provider);

    List<UserApiKey> findByUserIdAndKeyTypeAndIsActiveTrue(Long userId, UserApiKey.KeyType keyType);

    /** Find the default key for a given (user, provider, keyType) slot. */
    Optional<UserApiKey> findByUserIdAndProviderAndKeyTypeAndIsDefaultTrueAndIsActiveTrue(
            Long userId, String provider, UserApiKey.KeyType keyType);

    Optional<UserApiKey> findByIdAndUserId(Long id, Long userId);
}
