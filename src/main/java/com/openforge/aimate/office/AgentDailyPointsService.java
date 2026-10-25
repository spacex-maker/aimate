package com.openforge.aimate.office;

import com.openforge.aimate.domain.AgentDailyPoints;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.repository.AgentDailyPointsRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDailyPointsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int SPEND_RETRIES = 5;

    private final AgentDailyPointsRepository dailyPointsRepository;
    private final UserAgentRepository userAgentRepository;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public long spentToday(Long userAgentId) {
        if (userAgentId == null) return 0L;
        return dailyPointsRepository.findByUserAgentIdAndDay(userAgentId, today())
                .map(AgentDailyPoints::getSpentPoints)
                .orElse(0L);
    }

    @Transactional(readOnly = true)
    public void assertWithinDailyLimit(Long userAgentId, long upcomingAmount) {
        if (userAgentId == null || upcomingAmount <= 0) return;
        UserAgent ua = userAgentRepository.findById(userAgentId).orElse(null);
        if (ua == null || ua.getDailyPointsLimit() == null || ua.getDailyPointsLimit() <= 0) return;
        long spent = spentToday(userAgentId);
        if (spent + upcomingAmount > ua.getDailyPointsLimit()) {
            throw new AgentDailyLimitExceededException(
                    "该 Agent 今日积分已达上限（" + ua.getDailyPointsLimit() + "），请明日再试或调高限额");
        }
    }

    /**
     * 记录消耗。群内多路并行投票时同一日积分行会并发写，独立事务 + 乐观锁重试，
     * 避免 SMART 意图识别因积分写入失败被吞掉。
     */
    public void recordSpend(Long userId, Long userAgentId, long amount) {
        if (userAgentId == null || amount <= 0) return;
        RuntimeException last = null;
        for (int i = 0; i < SPEND_RETRIES; i++) {
            try {
                transactionTemplate.executeWithoutResult(status -> persistSpend(userId, userAgentId, amount));
                return;
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
                last = e instanceof RuntimeException re ? re
                        : new ObjectOptimisticLockingFailureException(AgentDailyPoints.class, userAgentId, e);
                log.debug("[DailyPoints] optimistic lock retry {} agent={}", i + 1, userAgentId);
            } catch (DataIntegrityViolationException e) {
                // 并发首次插入同一 (agent,day) 唯一键冲突：下一轮改为更新
                last = e;
                log.debug("[DailyPoints] unique conflict retry {} agent={}", i + 1, userAgentId);
            }
        }
        throw last != null ? last
                : new ObjectOptimisticLockingFailureException(AgentDailyPoints.class, userAgentId);
    }

    private void persistSpend(Long userId, Long userAgentId, long amount) {
        LocalDate day = today();
        AgentDailyPoints row = dailyPointsRepository.findByUserAgentIdAndDay(userAgentId, day)
                .orElseGet(() -> {
                    AgentDailyPoints n = new AgentDailyPoints();
                    n.setUserAgentId(userAgentId);
                    n.setUserId(userId != null ? userId : 0L);
                    n.setDay(day);
                    n.setSpentPoints(0);
                    return n;
                });
        if (userId != null) row.setUserId(userId);
        row.setSpentPoints(row.getSpentPoints() + amount);
        dailyPointsRepository.saveAndFlush(row);
    }

    public LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
