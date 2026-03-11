package com.openforge.aimate.repository;

import com.openforge.aimate.domain.UserDefaultModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserDefaultModelRepository extends JpaRepository<UserDefaultModel, Long> {

    /** 查找某个用户当前的首选系统模型（最近一次在输入框点击的模型）。 */
    Optional<UserDefaultModel> findByUserId(Long userId);
}

