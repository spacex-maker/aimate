package com.openforge.aimate.repository;

import com.openforge.aimate.domain.UserEmbeddingModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserEmbeddingModelRepository extends JpaRepository<UserEmbeddingModel, Long> {

    List<UserEmbeddingModel> findByUserIdAndIsActiveTrue(Long userId);

    Optional<UserEmbeddingModel> findByUserIdAndIsDefaultTrueAndIsActiveTrue(Long userId);

    Optional<UserEmbeddingModel> findByIdAndUserId(Long id, Long userId);
}
